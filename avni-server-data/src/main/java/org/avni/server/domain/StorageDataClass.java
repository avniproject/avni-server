package org.avni.server.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

public enum StorageDataClass {
    MODEL("model"),
    GUIDANCE("guidance"),
    DEFAULT("default");

    // Reserved, non-assignable org media directories; each routes independently.
    public static final String MODEL_NAMESPACE = "models";
    public static final String GUIDANCE_NAMESPACE = "guidance";

    private static final Map<String, StorageDataClass> BY_NAMESPACE = new LinkedHashMap<>();

    static {
        BY_NAMESPACE.put(MODEL_NAMESPACE, MODEL);
        BY_NAMESPACE.put(GUIDANCE_NAMESPACE, GUIDANCE);
    }

    private final String configName;

    StorageDataClass(String configName) {
        this.configName = configName;
    }

    public static StorageDataClass dataClassForKey(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null) {
            return DEFAULT;
        }
        String path = pathOf(objectKeyOrUrl);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        // match a namespace only as a real path segment, never a bare substring like mymodels/ or photo-models.png
        for (Map.Entry<String, StorageDataClass> entry : BY_NAMESPACE.entrySet()) {
            String prefix = entry.getKey() + "/";
            if (path.startsWith(prefix) || path.contains("/" + prefix)) {
                return entry.getValue();
            }
        }
        return DEFAULT;
    }

    private static String pathOf(String objectKeyOrUrl) {
        if (objectKeyOrUrl.contains("://")) {
            try {
                String p = new URI(objectKeyOrUrl).getPath();
                return p == null ? "" : p;
            } catch (URISyntaxException e) {
                return objectKeyOrUrl;
            }
        }
        return objectKeyOrUrl;
    }

    public String getConfigName() {
        return configName;
    }
}
