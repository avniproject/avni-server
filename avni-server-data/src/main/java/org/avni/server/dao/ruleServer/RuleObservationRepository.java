package org.avni.server.dao.ruleServer;

import io.micrometer.observation.Observation;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.RepositoryProvider;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.jsRuleSupport.JsModelObservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RuleObservationRepository {
    private final ConceptRepository conceptRepository;

    @Autowired
    public RuleObservationRepository(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    public JsModelObservation findObservation(ObservationCollection observations, String conceptNameOrUuid, String parentConceptNameOrUuid) {
        if (StringUtils.isEmpty(parentConceptNameOrUuid)) {
            return findObservation(observations, conceptNameOrUuid);
        } else {
            Optional<Map.Entry<String, Object>> obsEntry = getObsEntry(observations, parentConceptNameOrUuid);
            if (obsEntry.isEmpty()) {
                return null;
            }
            return this.findObservation(new ObservationCollection((Map<String, Object>) obsEntry.get().getValue()), conceptNameOrUuid);
        }
    }

    public JsModelObservation findObservation(ObservationCollection observations, String conceptNameOrUuid) {
        Optional<Map.Entry<String, Object>> observation = getObsEntry(observations, conceptNameOrUuid);
        if (observation.isEmpty()) {
            return null;
        }
        Concept concept = conceptRepository.findByUuid(conceptNameOrUuid);
        return new JsModelObservation(concept.getName(), observation.get().getValue());
    }

    private Optional<Map.Entry<String, Object>> getObsEntry(ObservationCollection observationCollection, String conceptNameOrUuid) {
        return observationCollection.entrySet().stream()
                .filter(observationEntry -> {
                    String uuid = observationEntry.getKey();
                    Concept concept = conceptRepository.findByUuid(uuid);
                    return concept.getName().equals(conceptNameOrUuid) ||
                            concept.getUuid().equals(conceptNameOrUuid);
                })
                .findFirst();
    }
}
