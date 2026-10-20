package org.avni.server.service.abha;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.service.OrganisationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Keeps abha_link, which subject holds which ABHA number, in step with the subject's "ABHA Number" and "ABHA Address"
// answers. Never logs the number or the address.
@Service
public class AbhaLinkService {
    public static final String ABHA_NUMBER_CONCEPT = "ABHA Number";
    public static final String ABHA_ADDRESS_CONCEPT = "ABHA Address";

    private final OrganisationConfigService organisationConfigService;
    private final ConceptRepository conceptRepository;
    private final AbhaLinkRepository abhaLinkRepository;

    @Autowired
    public AbhaLinkService(OrganisationConfigService organisationConfigService, ConceptRepository conceptRepository, AbhaLinkRepository abhaLinkRepository) {
        this.organisationConfigService = organisationConfigService;
        this.conceptRepository = conceptRepository;
        this.abhaLinkRepository = abhaLinkRepository;
    }

    // Called after every save of a subject. Organisations with ABHA off pay one look at their settings and nothing more.
    public void syncLink(Individual individual) {
        if (!organisationConfigService.isAbhaEnabled()) {
            return;
        }
        String number = digitsOnly(answer(individual, ABHA_NUMBER_CONCEPT));
        if (number.isEmpty()) {
            return;
        }
        AbhaLink link = new AbhaLink();
        link.assignUUID();
        link.setIndividual(individual);
        link.setAbhaNumber(number);
        link.setAbhaAddress(answer(individual, ABHA_ADDRESS_CONCEPT));
        abhaLinkRepository.save(link);
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
