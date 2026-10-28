package org.avni.server.web.response.impl;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.Map;

public record EncounterWithLocationResponse(
        String encounterUuid,
        String encounterTypeName,
        DateTime encounterDateTime,
        DateTime earliestScheduledDate,
        boolean voided,
        SubjectSummary subject,
        // Display name of the user who last modified the encounter. For
        // completed reviews this is effectively the reviewer (i.e. the user
        // who flipped encounterDateTime from null). Null for scheduled rows
        // that have never been touched.
        String reviewedBy
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
        String reviewerName = null;
        if (encounter.getEncounterDateTime() != null) {
            User reviewer = encounter.getLastModifiedBy();
            if (reviewer != null) {
                reviewerName = reviewer.getName() != null && !reviewer.getName().isBlank()
                        ? reviewer.getName()
                        : reviewer.getUsername();
            }
        }
        return new EncounterWithLocationResponse(
                encounter.getUuid(),
                encounter.getEncounterType().getName(),
                encounter.getEncounterDateTime(),
                encounter.getEarliestVisitDateTime(),
                encounter.isVoided(),
                new SubjectSummary(subject.getUuid(), subject.getLegacyId(), displayName, location),
                reviewerName
        );
    }
}
