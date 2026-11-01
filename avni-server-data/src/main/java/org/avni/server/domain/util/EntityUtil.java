package org.avni.server.domain.util;

public class EntityUtil {
    /**
     * Names of subject types, programmes and encounter types are matched by rules and lookups, so a leading or trailing
     * space makes them silently never match (avni-client#1635). Spaces inside the name are kept.
     */
    public static String trimName(String name) {
        return name == null ? null : name.trim();
    }

    public static String getVoidedName(String name, Long id) {
        return String.format("%s (voided~%d)", name, id);
    }
}
