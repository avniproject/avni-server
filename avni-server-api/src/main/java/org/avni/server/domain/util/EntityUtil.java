package org.avni.server.domain.util;

public class EntityUtil {
    public static String getVoidedName(String name, Long id) {
        return String.format("%s (voided~%d)", name, id);
    }
}
