package org.avni.server.web.request;

/**
 * Ops/admin write request for the server-only model key store (avniproject/avni-server#1020).
 * The {@code key} is the model's plaintext (base64) AES key; it is encrypted on write and is never
 * returned in any web response or written to any reference-data record.
 */
public class ModelKeyRequest {
    private String sha256;
    private String key;

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
