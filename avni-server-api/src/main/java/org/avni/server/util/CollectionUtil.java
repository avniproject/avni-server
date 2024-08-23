package org.avni.server.util;

import org.avni.server.domain.CHSBaseEntity;
import org.springframework.util.StringUtils;

import java.util.List;

public class CollectionUtil {
    public static <T> T findByUuid(List<? extends CHSBaseEntity> list, String uuid) {
        return (T) list.stream().filter(x -> x.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    private static List<String> trimEnd(List<String> values) {
        int i = values.size() - 1;
        while (i >= 0 && StringUtils.isEmpty(values.get(i))) {
            i--;
        }
        return values.subList(0, i + 1);
    }

    private static boolean hasNoEmptyStrings(List<String> values) {
        return values.stream().noneMatch(StringUtils::isEmpty);
    }

    public static boolean isEmpty(List<String> values) {
        return values.stream().allMatch(StringUtils::isEmpty);
    }

    public static boolean hasOnlyTrailingEmptyStrings(List<String> values) {
        List<String> trimmed = trimEnd(values);
        return hasNoEmptyStrings(trimmed);
    }

    public static boolean anyStartsWith(List<String> values, String prefix) {
        return values.stream().anyMatch(x -> !StringUtils.isEmpty(x) && x.startsWith(prefix));
    }
}
