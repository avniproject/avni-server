package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

// Server-only store of the model's AES key, encrypted at rest. Keyed by org + sha256 of the plaintext blob.
@Entity
@Table(name = "model_key")
@BatchSize(size = 100)
public class ModelKey extends OrganisationAwareEntity {

    @NotNull
    @Column
    private String sha256;

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
