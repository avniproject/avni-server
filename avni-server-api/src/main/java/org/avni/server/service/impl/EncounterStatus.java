package org.avni.server.service.impl;

public enum EncounterStatus {
    SCHEDULED,
    COMPLETED,
    ALL;

    public static EncounterStatus from(String value) {
        if (value == null || value.isBlank()) return ALL;
        return switch (value.toLowerCase()) {
            case "scheduled" -> SCHEDULED;
            case "completed" -> COMPLETED;
            case "all" -> ALL;
            default -> throw new IllegalArgumentException(
                    "status must be one of scheduled, completed, all");
        };
    }
}
