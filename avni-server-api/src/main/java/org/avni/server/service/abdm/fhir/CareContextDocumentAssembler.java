package org.avni.server.service.abdm.fhir;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.*;
import org.avni.server.service.FormMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

// DEMO-ONLY abdm-hackathon: never merged.
// An Avni encounter and its patient as a CareContextDocument. Call inside a transaction (lazy observations, concepts)
// and with an organisation's user context (AbdmOrgContext.runAsOrg): ConceptRepository.findByUuid reads it.
// Values are resolved here rather than through ConceptService.getObservationValue, which needs an API request context
// (ApiRequestContextHolder) that a callback thread does not have: coded answers become answer names, everything else
// is passed through raw with its concept's data type and unit, and FhirBundleBuilder decides what is shared.
@Component
public class CareContextDocumentAssembler {
    private static final Logger logger = LoggerFactory.getLogger(CareContextDocumentAssembler.class);

    private final ConceptRepository conceptRepository;
    private final FormMappingService formMappingService;

    @Autowired
    public CareContextDocumentAssembler(ConceptRepository conceptRepository, FormMappingService formMappingService) {
        this.conceptRepository = conceptRepository;
        this.formMappingService = formMappingService;
    }

    public CareContextDocument assemble(Encounter encounter, Individual individual, String abhaNumber, String abhaAddress,
                                        String hipId, String hipName) {
        EncounterType encounterType = encounter.getEncounterType();
        Instant visitTime = encounter.getEncounterDateTime() == null ? null : Instant.ofEpochMilli(encounter.getEncounterDateTime().getMillis());
        return new CareContextDocument(
                encounter.getUuid(),
                encounterType == null ? null : encounterType.getName(),
                visitTime,
                individual.getUuid(),
                patientName(individual),
                individual.getGender() == null ? null : individual.getGender().getName(),
                individual.getDateOfBirth(),
                abhaNumber,
                abhaAddress,
                practitionerName(encounter),
                hipId,
                hipName,
                items(encounter, encounterType));
    }

    private List<CareContextDocument.Item> items(Encounter encounter, EncounterType encounterType) {
        ObservationCollection observations = encounter.getObservations();
        if (observations == null || observations.isEmpty()) return List.of();

        List<Concept> concepts = new ArrayList<>();
        for (String conceptUuid : observations.keySet()) {
            Concept concept = conceptRepository.findByUuid(conceptUuid);
            if (concept != null) concepts.add(concept);
        }
        Map<String, Integer> formOrder = formOrder(encounterType);
        concepts.sort(Comparator.<Concept>comparingInt(c -> formOrder.getOrDefault(c.getUuid(), Integer.MAX_VALUE))
                .thenComparing(Concept::getName, Comparator.nullsLast(Comparator.naturalOrder())));

        List<CareContextDocument.Item> items = new ArrayList<>();
        for (Concept concept : concepts) {
            Object value = value(concept, observations.get(concept.getUuid()));
            if (value != null) items.add(new CareContextDocument.Item(concept.getName(), concept.getDataType(), value, concept.getUnit()));
        }
        return items;
    }

    private Object value(Concept concept, Object raw) {
        if (raw == null || !ConceptDataType.Coded.name().equals(concept.getDataType())) return raw;
        if (raw instanceof String answerUuid) return answerName(answerUuid);
        if (raw instanceof Collection<?> answers) {
            List<String> names = new ArrayList<>();
            for (Object answer : answers) {
                String name = answer instanceof String s ? answerName(s) : null;
                if (name != null) names.add(name);
            }
            return names.isEmpty() ? null : names;
        }
        return null;
    }

    // An answer whose concept is gone is dropped rather than shown to a doctor as a UUID.
    private String answerName(String answerUuid) {
        Concept answer = conceptRepository.findByUuid(answerUuid);
        return answer == null ? null : answer.getName();
    }

    // Concept uuid to its position on the encounter's form. Empty when the form cannot be found: answers then go by name.
    private Map<String, Integer> formOrder(EncounterType encounterType) {
        Map<String, Integer> order = new HashMap<>();
        if (encounterType == null) return order;
        try {
            FormMapping mapping = formMappingService.find(encounterType, FormType.Encounter);
            if (mapping == null || mapping.getForm() == null) return order;
            int position = 0;
            for (FormElement element : mapping.getForm().getAllFormElements()) {
                if (element.getConcept() != null) order.putIfAbsent(element.getConcept().getUuid(), position++);
            }
        } catch (RuntimeException e) {
            // DEMO-ONLY: ordering is cosmetic, so a form that cannot be read must not stop the share.
            logger.warn("ABDM: encounter form not read for ordering answers ({})", e.getClass().getSimpleName());
        }
        return order;
    }

    private static String patientName(Individual individual) {
        String name = String.join(" ", nonBlank(individual.getFirstName(), individual.getMiddleName(), individual.getLastName()));
        return name.isBlank() ? null : name;
    }

    private static String practitionerName(Encounter encounter) {
        User user = encounter.getFilledBy() != null ? encounter.getFilledBy() : encounter.getLastModifiedBy();
        return user == null ? null : user.getName();
    }

    private static List<String> nonBlank(String... parts) {
        List<String> out = new ArrayList<>();
        for (String part : parts) if (part != null && !part.isBlank()) out.add(part.trim());
        return out;
    }
}
