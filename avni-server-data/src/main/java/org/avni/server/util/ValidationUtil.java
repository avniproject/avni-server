package org.avni.server.util;

import org.avni.server.domain.ValidationException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ValidationUtil {
    public static final Pattern COMMON_INVALID_CHARS_PATTERN = Pattern.compile("^.*[<>=\"'].*$");
    public static final Pattern COMMON_INVALID_CHARS = Pattern.compile("<> = \" '");
    public static final Pattern NAME_INVALID_CHARS_PATTERN = Pattern.compile("^.*[<>=\"].*$");
    public static final Pattern NAME_INVALID_CHARS = Pattern.compile("<> = \"");

    public static boolean checkNull(Object checkObject) {
        return checkObject == null;
    }

    public static boolean checkEmptyString(String checkString) {
        return checkString.trim().isEmpty();
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

    public static void handleErrors(List<String> errorMsgs) throws ValidationException {
        if (!errorMsgs.isEmpty()) {
            errorMsgs = errorMsgs.stream().distinct().sorted().collect(Collectors.toList()); // sorted for predictability in tests
            throw new ValidationException(String.join(", ", errorMsgs));
        }
    }

    public static void fieldMissing(String fieldName, String value, List<String> errorMessages) {
        errorMessages.add(String.format("%s '%s' not found", fieldName, value == null ? "" : value));
    }
}
