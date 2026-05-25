package org.avni.server.util;

import org.avni.server.domain.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CalendarWorkingPatternValidator {

    private static final List<String> DAY_KEYS = Arrays.asList("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    private CalendarWorkingPatternValidator() {
    }

    public static void validate(JsonObject pattern) {
        if (pattern == null) {
            throw new BadRequestError("workingPattern is required");
        }
        if (pattern.size() != DAY_KEYS.size() || !pattern.keySet().containsAll(DAY_KEYS)) {
            throw new BadRequestError("workingPattern must contain exactly the keys: %s", DAY_KEYS);
        }
        for (String day : DAY_KEYS) {
            validateDayValue(day, pattern.get(day));
        }
    }

    private static void validateDayValue(String day, Object value) {
        if (value instanceof String) {
            String s = (String) value;
            if (!"all".equals(s) && !"none".equals(s)) {
                throw new BadRequestError("workingPattern.%s string value must be 'all' or 'none', got '%s'", day, s);
            }
            return;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            Set<Integer> seen = new HashSet<>();
            for (Object element : list) {
                int shift = toShiftInt(day, element);
                if (shift < 1 || shift > 5) {
                    throw new BadRequestError("workingPattern.%s shift values must be ints 1-5, got %s", day, element);
                }
                if (!seen.add(shift)) {
                    throw new BadRequestError("workingPattern.%s contains duplicate shift %d", day, shift);
                }
            }
            return;
        }
        throw new BadRequestError("workingPattern.%s must be 'all', 'none', or an array of ints 1-5", day);
    }

    private static int toShiftInt(String day, Object element) {
        if (element instanceof Number) {
            return ((Number) element).intValue();
        }
        throw new BadRequestError("workingPattern.%s shift values must be numeric, got %s", day, element);
    }
}
