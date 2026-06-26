package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

// Per-org storage credential, keyed by credentialRef. The secret key is stored encrypted at rest.
@Entity
@Table(name = "org_storage_credential")
@BatchSize(size = 100)
public class OrgStorageCredential extends OrganisationAwareEntity {

    @NotNull
    @Column(name = "credential_ref")
    private String credentialRef;

    @NotNull
    @Column(name = "access_key")
    private String accessKey;

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
