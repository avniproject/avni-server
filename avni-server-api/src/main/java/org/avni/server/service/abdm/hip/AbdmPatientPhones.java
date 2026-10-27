package org.avni.server.service.abdm.hip;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.service.ConceptService;
import org.avni.server.service.IndividualService;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

// DEMO-ONLY abdm-hackathon: never merged.
// A patient's phone number, read as the rest of Avni reads it (IndividualService.findPhoneNumber), in a read-only
// transaction as the organisation, and the concepts it is read from, for finding a patient by phone. Never logged.
@Component
public class AbdmPatientPhones {
    private static final Logger logger = LoggerFactory.getLogger(AbdmPatientPhones.class);

    private final IndividualRepository individualRepository;
    private final IndividualService individualService;
    private final AbdmOrgContext orgContext;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;

    public AbdmPatientPhones(IndividualRepository individualRepository, IndividualService individualService, AbdmOrgContext orgContext,
                             ConceptRepository conceptRepository, ConceptService conceptService) {
        this.individualRepository = individualRepository;
        this.individualService = individualService;
        this.orgContext = orgContext;
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
    }

    public record PhoneConcepts(String phoneNumberConceptUuid, String contactNumberConceptUuid) {
    }

    // The two concepts IndividualService.findPhoneNumber reads, in its order: the first of data type PhoneNumber, then the
    // contact-number text concept. Both null when they cannot be read.
    public PhoneConcepts phoneConcepts() {
        try {
            return orgContext.inReadOnlyTransaction(() -> new PhoneConcepts(
                    conceptRepository.findAllByDataType("PhoneNumber").stream().findFirst().map(Concept::getUuid).orElse(null),
                    conceptService.findContactNumberConcept().map(Concept::getUuid).orElse(null)));
        } catch (RuntimeException e) {
            logger.warn("Phone number concepts not read: {}", e.getClass().getSimpleName());
            return new PhoneConcepts(null, null);
        }
    }

    public Optional<String> phoneOf(long individualId) {
        try {
            return Optional.ofNullable(orgContext.inReadOnlyTransaction(() -> {
                Individual individual = individualRepository.findEntity(individualId);
                if (individual == null || individual.isVoided()) return null;
                String phone = individualService.findPhoneNumber(individual);
                return StringUtils.hasText(phone) ? phone.trim() : null;
            }));
        } catch (RuntimeException e) {
            logger.warn("Phone number of subject {} not read: {}", individualId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
