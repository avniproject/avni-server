package org.avni.server.util;

import org.avni.server.domain.CHSBaseEntity;

import java.util.List;

public class CollectionUtil {
    public static <T> T findByUuid(List<? extends CHSBaseEntity> list, String uuid) {
        return (T) list.stream().filter(x -> x.getUuid().equals(uuid)).findFirst().orElse(null);
    }
}
