package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

/**
 * Server-only model key store (avniproject/avni-server#1020, D19/D20).
 * <p>
 * Holds the edge model's AES key <b>encrypted at rest</b> via {@code CryptoService} (AES/GCM,
 * IV-prefixed) under a base64 deploy master key - mirroring the {@code Msg91Config} /
 * {@code OrgStorageCredential} (story-3 cred store) precedent.
 * <p>
 * Keyed by org + {@link #sha256} (the SHA-256 of the plaintext blob), so each ensemble fold's key is
 * addressable and the lookup matches the {@code models/<sha256>.bin} object-key convention.
 * <p>
 * The plaintext AES key is <b>never</b> stored here, <b>never</b> written to a subject/ref-data
 * observation, and <b>never</b> returned in any {@code /web/...} response. It is decrypted and served
 * only by the device key-delivery endpoint ({@code GET /media/modelKey}), which - unlike Msg91 -
 * deliberately returns the REAL (unmasked) key (D19).
 * <p>
 * RLS-enabled (extends {@link OrganisationAwareEntity}); org A cannot read org B's key.
 */
@Entity
@Table(name = "model_key")
@BatchSize(size = 100)
public class ModelKey extends OrganisationAwareEntity {

    /**
     * SHA-256 of the plaintext blob - the addressable lookup key, matching {@code models/<sha256>.bin}.
     * Unique per org.
     */
    @NotNull
    @Column
    private String sha256;

    /**
     * The model's AES key, AES/GCM-encrypted at rest (base64 of IV-prefixed ciphertext). Plaintext is
     * only ever held transiently after decryption and is served only via the device key endpoint.
     */
    @NotNull
    @Column(name = "encrypted_key")
    private String encryptedKey;

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }
}
