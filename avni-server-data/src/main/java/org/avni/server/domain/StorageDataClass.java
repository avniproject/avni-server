package org.avni.server.domain;

/**
 * The class of data being stored / served, used to route a request to the right storage backend
 * per-org (avniproject/avni-server#1012).
 * <p>
 * P0 values:
 * <ul>
 *     <li>{@link #MODEL} - edge-model blobs ({@code models/} object-key prefix); an org may host these
 *     on its own cloud (e.g. GCS).</li>
 *     <li>{@link #DEFAULT} - everything else (media / extensions / exports / bulk-upload). Resolves to
 *     today's backend for an org with no storage routing configured, byte-for-byte (D16).</li>
 * </ul>
 */
public enum StorageDataClass {
    MODEL("model"),
    DEFAULT("default");

    private final String configName;

    StorageDataClass(String configName) {
        this.configName = configName;
    }

    /**
     * The lower-camel name used as the key in the {@code storageBackends} org-config map
     * (e.g. {@code {"storage":{"model":"org-gcs","default":"avni-s3"}}}).
     */
    public String getConfigName() {
        return configName;
    }
}
