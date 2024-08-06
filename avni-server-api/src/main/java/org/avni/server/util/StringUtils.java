package org.avni.server.util;

public class StringUtils {
    public static String toSnakeCase(String input) {
        if (input == null) {
            return null;
        }
        return input.trim().replaceAll(" +", "_").toLowerCase();
    }
}
