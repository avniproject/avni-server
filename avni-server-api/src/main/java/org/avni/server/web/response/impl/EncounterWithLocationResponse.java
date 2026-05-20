package org.avni.server.web.response.impl;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.Map;

public record EncounterWithLocationResponse(
        String encounterUuid,
        String encounterTypeName,
        DateTime encounterDateTime,
        DateTime earliestScheduledDate,
        boolean voided,
        SubjectSummary subject
) {
    public static EncounterWithLocationResponse from(Encounter encounter) {
        Individual subject = encounter.getIndividual();
        Map<String, String> location = new LinkedHashMap<>();
        AddressLevel cursor = subject.getAddressLevel();
        while (cursor != null) {
            location.put(cursor.getType().getName(), cursor.getTitle());
            cursor = cursor.getParent();
        }
        String first = subject.getFirstName() == null ? "" : subject.getFirstName();
        String last = subject.getLastName() == null ? "" : subject.getLastName();
        String displayName = (first + " " + last).trim();
        return new EncounterWithLocationResponse(
                encounter.getUuid(),
                encounter.getEncounterType().getName(),
                encounter.getEncounterDateTime(),
                encounter.getEarliestVisitDateTime(),
                encounter.isVoided(),
                new SubjectSummary(subject.getUuid(), subject.getLegacyId(), displayName, location)
        );
    }
}
