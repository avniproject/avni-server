package org.avni.server.service;

import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.ObservationCollection;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MediaObservationService {
    private final ObservationService observationService;
    private final S3Service s3Service;

    public MediaObservationService(ObservationService observationService, S3Service s3Service) {
        this.observationService = observationService;
        this.s3Service = s3Service;
    }

    public void processMediaObservations(ObservationCollection allEntityObservations) throws IOException {
        Map<Concept, Object> mediaObservations = observationService.filterObservationsByDataType(ConceptDataType.mediaDataTypes, allEntityObservations);
        for (Map.Entry<Concept, Object> entry : mediaObservations.entrySet()) {
            Concept concept = entry.getKey();
            putObservation(allEntityObservations, entry, concept);
        }
    }

    public void patchMediaObservations(ObservationCollection allEntityObservations, Set<String> conceptsReceived) throws IOException {
        Map<Concept, Object> mediaObservations = observationService.filterObservationsByDataType(ConceptDataType.mediaDataTypes, allEntityObservations);
        for (Map.Entry<Concept, Object> entry : mediaObservations.entrySet()) {
            Concept concept = entry.getKey();
            if (conceptsReceived.stream().noneMatch(s -> s.equals(concept.getName())))
                continue;

            putObservation(allEntityObservations, entry, concept);
        }
    }

    private void putObservation(ObservationCollection allEntityObservations, Map.Entry<Concept, Object> entry, Concept concept) throws IOException {
        if (entry.getValue() instanceof String) {
            String value = (String) entry.getValue();
            String newValue = copyMediaToAvni(value);
            allEntityObservations.put(concept.getUuid(), newValue);
        } else {
            List<String> values = (List<String>) entry.getValue();
            List<String> newValues = new ArrayList<>();
            for (String value : values) {
                String newValue = copyMediaToAvni(value);
                newValues.add(newValue);
            }
            allEntityObservations.put(concept.getUuid(), newValues);
        }
    }

    private String copyMediaToAvni(String value) throws IOException {
        if (s3Service.isInternalUrl(value)) {
            return value;
        }
        File file = s3Service.downloadExternalFile(value);
        return s3Service.uploadFileToS3(file);
    }
}
