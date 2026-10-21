package org.avni.server.service.abha;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Keeps abha_link, which subject holds which ABHA number, in step with the subject's "ABHA Number" and "ABHA Address"
// answers. Never logs the number or the address.
@Service
public class AbhaLinkService {
    public static final String ABHA_NUMBER_CONCEPT = "ABHA Number";
    public static final String ABHA_ADDRESS_CONCEPT = "ABHA Address";
    public static final int ABHA_NUMBER_DIGITS = 14;
    private static final Logger logger = LoggerFactory.getLogger(AbhaLinkService.class);

    private final OrganisationConfigService organisationConfigService;
    private final ConceptRepository conceptRepository;
    private final AbhaLinkRepository abhaLinkRepository;
    private final AbhaRemovedEventWriter abhaRemovedEventWriter;

    @Autowired
    public AbhaLinkService(OrganisationConfigService organisationConfigService, ConceptRepository conceptRepository, AbhaLinkRepository abhaLinkRepository,
                           AbhaRemovedEventWriter abhaRemovedEventWriter) {
        this.organisationConfigService = organisationConfigService;
        this.conceptRepository = conceptRepository;
        this.abhaLinkRepository = abhaLinkRepository;
        this.abhaRemovedEventWriter = abhaRemovedEventWriter;
    }

    // Called after every save or void of a subject. Organisations with ABHA off pay one look at their settings and
    // nothing more. A voided subject, or one with no ABHA Number answer, has no live link; otherwise its one live link
    // holds the current answers, updated in place when they change.
    public void syncLink(Individual individual) {
        if (!organisationConfigService.isAbhaEnabled()) {
            return;
        }
        Optional<AbhaLink> liveLink = abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(individual.getId());
        String number = individual.isVoided() ? "" : digitsOnly(answer(individual, ABHA_NUMBER_CONCEPT));
        if (!number.isEmpty() && number.length() != ABHA_NUMBER_DIGITS) {
            // Not an ABHA number, and too long for the column: linking it would refuse the save.
            logger.warn("ABHA Number answer of subject {} is not {} digits; no ABHA link kept", individual.getUuid(), ABHA_NUMBER_DIGITS);
            number = "";
        }
        if (number.isEmpty()) {
            liveLink.ifPresent(link -> {
                link.setVoided(true);
                abhaLinkRepository.save(link);
                abhaRemovedEventWriter.recordRemoved(individual.getUuid());
            });
            return;
        }
        String address = answer(individual, ABHA_ADDRESS_CONCEPT);
        AbhaLink link = liveLink.orElseGet(() -> {
            AbhaLink newLink = new AbhaLink();
            newLink.assignUUID();
            newLink.setIndividual(individual);
            return newLink;
        });
        if (link.getId() != null && number.equals(link.getAbhaNumber()) && Objects.equals(address, link.getAbhaAddress())) {
            return;
        }
        boolean numberChanged = !number.equals(link.getAbhaNumber());
        link.setAbhaNumber(number);
        link.setAbhaAddress(address);
        abhaLinkRepository.save(link);
        if (numberChanged) {
            warnIfHeldByOthers(number);
        }
    }

    // Sync is never refused for a duplicate: the app blocks one when the ABHA is found, and other routes (an upload,
    // another system) are left to the warning.
    private void warnIfHeldByOthers(String number) {
        List<String> holders = abhaLinkRepository.findLiveSubjectUUIDs(UserContextHolder.getUserContext().getOrganisationId(), number);
        if (holders.size() > 1) {
            logger.warn("One ABHA number is held by {} live subjects: {}", holders.size(), holders);
        }
    }

    // The live subjects of this subject type in the user's organisation holding this number, however it is written.
    public List<String> findSubjectUUIDs(SubjectType subjectType, String abhaNumber) {
        return abhaLinkRepository.findLiveSubjectUUIDsOfType(UserContextHolder.getUserContext().getOrganisationId(), digitsOnly(abhaNumber), subjectType.getId());
    }

    // Eka writes 12-3456-7856-8912; a person may type the 14 digits with spaces or nothing between them.
    static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String answer(Individual individual, String conceptName) {
        Concept concept = conceptRepository.findByName(conceptName);
        if (concept == null || individual.getObservations() == null) {
            return null;
        }
        String value = individual.getObservations().getStringValue(concept.getUuid());
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
