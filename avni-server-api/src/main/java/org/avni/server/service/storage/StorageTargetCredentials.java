package org.avni.server.service.storage;

/**
 * A resolved (decrypted) access-key / secret-key pair for a storage target. Held transiently while
 * building the target's S3 client; never serialised, never persisted in plaintext.
 */
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
