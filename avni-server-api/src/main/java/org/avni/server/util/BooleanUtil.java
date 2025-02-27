package org.avni.server.util;

public class BooleanUtil {
    public static boolean getBoolean(Boolean b, boolean nullDefaultsTo) {
        if (b == null) {
            return nullDefaultsTo;
        }
        return b;
    }
}
