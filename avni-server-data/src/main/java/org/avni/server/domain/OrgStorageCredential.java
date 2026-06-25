package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

/**
 * Encrypted-per-org storage credential store (avniproject/avni-server#1012, D14).
 * <p>
 * Holds the access-key / secret-key pair for a named storage target (referenced by a target's
 * {@code credentialRef}), keyed by org + {@link #credentialRef}. The secret is stored
 * <b>encrypted at rest</b> via {@code CryptoService} (AES/GCM, IV-prefixed) under a base64 deploy
 * master key - mirroring the {@code Msg91Config} / story-5 key-store precedent. <b>No plaintext
 * credential is ever stored in org config.</b>
 * <p>
 * RLS-enabled (extends {@link OrganisationAwareEntity}); org A cannot read org B's credentials.
 */
@Entity
@Table(name = "org_storage_credential")
@BatchSize(size = 100)
public class OrgStorageCredential extends OrganisationAwareEntity {

    /**
     * Logical name of the storage target this credential belongs to (matches a target's
     * {@code credentialRef}). Unique per org.
     */
    @NotNull
    @Column(name = "credential_ref")
    private String credentialRef;

    /**
     * Access key (HMAC interop key for GCS; access key id for S3/MinIO). Not a secret by itself,
     * stored in the clear.
     */
    @NotNull
    @Column(name = "access_key")
    private String accessKey;

    /**
     * Secret access key, AES/GCM-encrypted at rest (base64 of IV-prefixed ciphertext). Plaintext
     * is only ever held transiently after decryption.
     */
    @NotNull
    @Column(name = "encrypted_secret_key")
    private String encryptedSecretKey;

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getEncryptedSecretKey() {
        return encryptedSecretKey;
    }

    public void setEncryptedSecretKey(String encryptedSecretKey) {
        this.encryptedSecretKey = encryptedSecretKey;
    }
}
