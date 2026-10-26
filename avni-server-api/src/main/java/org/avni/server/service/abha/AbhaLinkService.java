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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// Keeps abha_link, which subject holds which ABHA number, in step with the subject's "ABHA Number" and "ABHA Address"
// answers. Never logs the number or the address.
@Service
public class AbhaLinkService {
    public static final String ABHA_NUMBER_CONCEPT = "ABHA Number";
    public static final String ABHA_ADDRESS_CONCEPT = "ABHA Address";
    public static final int ABHA_NUMBER_DIGITS = 14;
    // abha_link.abha_address is varchar(255).
    static final int ABHA_ADDRESS_MAX_LENGTH = 255;
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
    // nothing more. A voided subject, or one with neither an ABHA Number nor an ABHA Address answer, has no live link;
    // otherwise its one live link holds the current answers, updated in place when they change. An ABHA made with a
    // mobile number only has an address and no number, and is linked by the address.
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
        String address = individual.isVoided() ? null : answer(individual, ABHA_ADDRESS_CONCEPT);
        if (address != null && address.isBlank()) address = null;
        if (address != null && address.length() > ABHA_ADDRESS_MAX_LENGTH) {
            // Too long for the column: linking it would refuse the save, and every retry of it. The number is still linked.
            logger.warn("ABHA Address answer of subject {} is longer than {} characters; linked without it", individual.getUuid(), ABHA_ADDRESS_MAX_LENGTH);
            address = null;
        }
        if (number.isEmpty() && address == null) {
            liveLink.ifPresent(link -> {
                link.setVoided(true);
                abhaLinkRepository.save(link);
                abhaRemovedEventWriter.recordRemoved(individual.getUuid());
            });
            return;
        }
        String linkedNumber = number.isEmpty() ? null : number;
        AbhaLink link = liveLink.orElseGet(() -> {
            AbhaLink newLink = new AbhaLink();
            newLink.assignUUID();
            newLink.setIndividual(individual);
            return newLink;
        });
        if (link.getId() != null && Objects.equals(linkedNumber, link.getAbhaNumber()) && Objects.equals(address, link.getAbhaAddress())) {
            return;
        }
        boolean numberChanged = linkedNumber != null && !linkedNumber.equals(link.getAbhaNumber());
        boolean addressChanged = address != null && !address.equalsIgnoreCase(Objects.toString(link.getAbhaAddress(), ""));
        link.setAbhaNumber(linkedNumber);
        link.setAbhaAddress(address);
        abhaLinkRepository.save(link);
        if (numberChanged) {
            warnIfHeldByOthers(abhaLinkRepository.findLiveSubjectUUIDs(organisationId(), linkedNumber), "number");
        } else if (linkedNumber == null && addressChanged) {
            warnIfHeldByOthers(abhaLinkRepository.findLiveSubjectUUIDsByAddress(organisationId(), address), "address");
        }
    }

    // Sync is never refused for a duplicate: the app blocks one when the ABHA is found, a bulk upload refuses one before it
    // saves (otherLiveHolders), and other routes (another system) are left to the warning.
    private void warnIfHeldByOthers(List<String> holders, String what) {
        if (holders.size() > 1) {
            logger.warn("One ABHA {} is held by {} live subjects: {}", what, holders.size(), holders);
        }
    }

    private static Long organisationId() {
        return UserContextHolder.getUserContext().getOrganisationId();
    }

    // Who else, live in the user's organisation, already holds the ABHA this subject answers: by its number, else, for
    // an ABHA with only an address, by the address. Empty with ABHA off, or with neither answer. A bulk upload refuses its
    // row on this (SubjectWriter); sync never asks.
    public List<String> otherLiveHolders(Individual individual) {
        if (!organisationConfigService.isAbhaEnabled()) {
            return Collections.emptyList();
        }
        String number = digitsOnly(answer(individual, ABHA_NUMBER_CONCEPT));
        String address = answer(individual, ABHA_ADDRESS_CONCEPT);
        List<String> holders;
        if (number.length() == ABHA_NUMBER_DIGITS) {
            holders = abhaLinkRepository.findLiveSubjectUUIDs(organisationId(), number);
        } else if (address != null && !address.isBlank()) {
            holders = abhaLinkRepository.findLiveSubjectUUIDsByAddress(organisationId(), address.trim());
        } else {
            return Collections.emptyList();
        }
        return holders.stream().filter(uuid -> !uuid.equals(individual.getUuid())).collect(Collectors.toList());
    }

    // The live subjects of this subject type in the user's organisation holding this number (however it is written) or
    // this address (in any case, with or without its @ suffix). Either may be missing.
    public List<String> findSubjectUUIDs(SubjectType subjectType, String abhaNumber, String abhaAddress) {
        java.util.TreeSet<String> holders = new java.util.TreeSet<>();
        String number = digitsOnly(abhaNumber);
        if (number.length() == ABHA_NUMBER_DIGITS) {
            holders.addAll(abhaLinkRepository.findLiveSubjectUUIDsOfType(organisationId(), number, subjectType.getId()));
        }
        if (abhaAddress != null && !abhaAddress.isBlank()) {
            holders.addAll(abhaLinkRepository.findLiveSubjectUUIDsOfTypeByAddress(organisationId(), abhaAddress.trim(), subjectType.getId()));
        }
        return new ArrayList<>(holders);
    }

    public List<String> findSubjectUUIDs(SubjectType subjectType, String abhaNumber) {
        return findSubjectUUIDs(subjectType, abhaNumber, null);
    }

    // The organisation's own question, else its parent's, and so on up. Never one query across them all, which expects
    // a single answer: a parent that adds the question after its child defined it would make every save in the child fail.
    private Concept nearestConcept(String conceptName) {
        for (Long organisationId : conceptRepository.buildOrganisationIdList()) {
            Concept concept = conceptRepository.findByNameAndOrganisationId(conceptName, Collections.singletonList(organisationId));
            if (concept != null) {
                return concept;
            }
        }
        return null;
    }

    // Eka writes 12-3456-7856-8912; a person may type the 14 digits with spaces or nothing between them.
    static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String answer(Individual individual, String conceptName) {
        Concept concept = nearestConcept(conceptName);
        if (concept == null || individual.getObservations() == null) {
            return null;
        }
        String value = individual.getObservations().getStringValue(concept.getUuid());
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
