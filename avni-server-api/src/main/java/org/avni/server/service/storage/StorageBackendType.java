package org.avni.server.service.storage;

import java.util.Arrays;

public enum StorageBackendType {
    S3,
    MINIO,
    GCS;

    public static StorageBackendType fromConfig(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Storage target 'type' is required (one of s3|minio|gcs)");
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Unknown storage target type '%s' (expected one of s3|minio|gcs)", value)));
    }
}
