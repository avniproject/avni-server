package org.avni.server.web.request.customimpl;

public record EncounterSearchRequest(
        String encounterType,
        String status,
        String locationUuid,
        String linkedEncounterType,
        String linkedObservationConceptUuid,
        String linkedLocationUuid
) {}
