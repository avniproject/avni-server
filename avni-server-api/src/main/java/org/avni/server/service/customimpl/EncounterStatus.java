package org.avni.server.service.customimpl;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum EncounterStatus {
    SCHEDULED,
    COMPLETED,
    ALL;

    public static EncounterStatus from(String value) {
        if (value == null || value.isBlank()) return ALL;
        try {
            return EncounterStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be one of scheduled, completed, all");
        }
    }
}
