package org.avni.server.domain;

import java.net.URI;
import java.net.URISyntaxException;

public enum StorageDataClass {
    MODEL("model"),
    DEFAULT("default");

    // Reserved as a non-assignable org media directory, so a DEFAULT key can never start with models/.
    public static final String MODEL_NAMESPACE = "models";

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
        // match models/ only as a real path segment, never a bare substring like mymodels/ or photo-models.png
        String prefix = MODEL_NAMESPACE + "/";
        if (path.startsWith(prefix) || path.contains("/" + prefix)) {
            return MODEL;
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
