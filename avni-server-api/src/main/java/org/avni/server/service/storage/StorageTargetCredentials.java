package org.avni.server.service.storage;

public class StorageTargetCredentials {
    private final String accessKey;
    private final String secretKey;

    public StorageTargetCredentials(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }
}
