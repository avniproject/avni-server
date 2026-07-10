package org.avni.server.service.storage;

import org.avni.server.domain.JsonObject;
import org.springframework.util.StringUtils;

import java.util.Map;

// Non-secret descriptor of a named storage target {type, endpoint, bucket, credentialRef}. Credentials are resolved separately.
public class StorageTarget {
    private final String name;
    private final StorageBackendType type;
    private final String endpoint;
    private final String bucket;
    private final String credentialRef;

    public StorageTarget(String name, StorageBackendType type, String endpoint, String bucket, String credentialRef) {
        this.name = name;
        this.type = type;
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.credentialRef = credentialRef;
    }

    public static StorageTarget fromConfig(String name, Map<String, Object> descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException(String.format("Storage target '%s' has no descriptor", name));
        }
        StorageBackendType type = StorageBackendType.fromConfig(asString(descriptor.get("type")));
        String bucket = asString(descriptor.get("bucket"));
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalArgumentException(String.format("Storage target '%s' is missing 'bucket'", name));
        }
        return new StorageTarget(name, type, asString(descriptor.get("endpoint")), bucket, asString(descriptor.get("credentialRef")));
    }

    @SuppressWarnings("unchecked")
    public static StorageTarget fromConfig(String name, JsonObject targets) {
        Object descriptor = targets == null ? null : targets.get(name);
        if (!(descriptor instanceof Map)) {
            throw new IllegalArgumentException(String.format("No storage target named '%s' is configured", name));
        }
        return fromConfig(name, (Map<String, Object>) descriptor);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString().trim();
    }

    public String getName() {
        return name;
    }

    public StorageBackendType getType() {
        return type;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public String getCredentialRef() {
        return credentialRef;
    }
}
