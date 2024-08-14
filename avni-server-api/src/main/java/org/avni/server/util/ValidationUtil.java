package org.avni.server.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidationUtil {
    public static final Pattern COMMON_INVALID_CHARS_PATTERN = Pattern.compile("^.*[<>=\"'].*$");
    public static final Pattern NAME_INVALID_CHARS_PATTERN = Pattern.compile("^.*[<>=\"].*$");

    public static boolean checkNull(Object checkObject) {
        return checkObject == null;
    }

    public static boolean checkEmptyString(String checkString) {
        return checkString.trim().equals("");
    }

    public static boolean containsDisallowedPattern(String checkString, Pattern pattern) {
        Matcher matcher = pattern.matcher(checkString);
        return matcher.find();
    }

    public static boolean checkNullOrEmpty(String checkString) {
        return (checkNull(checkString) || checkEmptyString(checkString));
    }

    public static boolean checkNullOrEmptyOrContainsDisallowedCharacters(String checkString, Pattern pattern) {
        return (checkNullOrEmpty(checkString) || containsDisallowedPattern(checkString, pattern));
    }
}
