package org.avni.server.service.abdm;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// DEMO-ONLY abdm-hackathon: never merged.
// ABDM's TIMESTAMP and date fields: ISO 8601 UTC with milliseconds always present. Instant.toString() drops ".000",
// which ABDM rejects.
public final class AbdmTime {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private AbdmTime() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    // Lenient read: ISO with or without milliseconds or zone; null when blank or unreadable.
    public static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value.endsWith("Z") || value.contains("+") ? value : value + "Z");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
