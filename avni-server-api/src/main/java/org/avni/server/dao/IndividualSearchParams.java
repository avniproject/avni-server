package org.avni.server.dao;

import org.avni.server.domain.Concept;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;

public class IndividualSearchParams {
    private final DateTime lastModifiedDateTime;
    private final DateTime now;
    private final String subjectTypeName;
    private final Map<Concept, String> observations;
    private final List<Long> allLocationIds;

    public IndividualSearchParams(DateTime lastModifiedDateTime, DateTime now, String subjectTypeName, Map<Concept, String> observations, List<Long> allLocationIds) {
        this.lastModifiedDateTime = lastModifiedDateTime;
        this.now = now;
        this.subjectTypeName = subjectTypeName;
        this.observations = observations;
        this.allLocationIds = allLocationIds;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public DateTime getNow() {
        return now;
    }

    public String getSubjectTypeName() {
        return subjectTypeName;
    }

    public Map<Concept, String> getObservations() {
        return observations;
    }

    public List<Long> getAllLocationIds() {
        return allLocationIds;
    }
}
