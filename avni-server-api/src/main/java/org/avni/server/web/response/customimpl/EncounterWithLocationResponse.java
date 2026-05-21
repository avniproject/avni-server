package org.avni.server.web.response.customimpl;

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
        String lastModifiedBy
) {
    public static EncounterWithLocationResponse from(Encounter encounter) {
        Individual subject = encounter.getIndividual();
        Map<String, String> location = new LinkedHashMap<>();
        AddressLevel cursor = subject.getAddressLevel();
        while (cursor != null) {
            String typeName = cursor.getType() == null ? null : cursor.getType().getName();
            location.put(typeName, cursor.getTitle());
            cursor = cursor.getParent();
        }
        String first = subject.getFirstName() == null ? "" : subject.getFirstName();
        String last = subject.getLastName() == null ? "" : subject.getLastName();
        String displayName = (first + " " + last).trim();
        User lastModifier = encounter.getLastModifiedBy();
        String lastModifiedByName = null;
        if (lastModifier != null) {
            lastModifiedByName = lastModifier.getName() != null && !lastModifier.getName().isBlank()
                    ? lastModifier.getName()
                    : lastModifier.getUsername();
        }
        return new EncounterWithLocationResponse(
                encounter.getUuid(),
                encounter.getEncounterType().getName(),
                encounter.getEncounterDateTime(),
                encounter.getEarliestVisitDateTime(),
                encounter.isVoided(),
                new SubjectSummary(subject.getUuid(), subject.getLegacyId(), displayName, location),
                lastModifiedByName
        );
    }
}
