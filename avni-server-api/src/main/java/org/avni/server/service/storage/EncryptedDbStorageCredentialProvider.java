package org.avni.server.service.storage;

import org.avni.server.dao.OrgStorageCredentialRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrgStorageCredential;
import org.avni.server.service.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * P0 {@link StorageCredentialProvider} (avniproject/avni-server#1012, D14): reads the credential for a
 * target's {@code credentialRef} from the encrypted-per-org DB store ({@link OrgStorageCredential}),
 * decrypting the secret via {@link CryptoService} under a base64 deploy master key - mirroring the
 * {@code Msg91Config} / story-5 key-store precedent.
 * <p>
 * Decrypt is load-bearing: a missing/incorrect master key throws (fails loud) rather than returning
 * a garbage secret that would only surface as an opaque S3 auth failure later. RLS on
 * {@code org_storage_credential} keeps org A out of org B's credentials.
 */
@Service
public class EncryptedDbStorageCredentialProvider implements StorageCredentialProvider {

    private final OrgStorageCredentialRepository repository;
    private final CryptoService cryptoService;
    private final String base64MasterKey;

    @Autowired
    public EncryptedDbStorageCredentialProvider(OrgStorageCredentialRepository repository,
                                                CryptoService cryptoService,
                                                @Value("${avni.storage.credentials.base64EncodedEncryptionKey}") String base64MasterKey) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.base64MasterKey = base64MasterKey;
    }

    @Override
    public StorageTargetCredentials getCredentials(Organisation organisation, String credentialRef) {
        if (organisation == null) {
            throw new StorageConfigurationException("Cannot resolve storage credentials without an organisation in context");
        }
        if (!StringUtils.hasText(credentialRef)) {
            throw new StorageConfigurationException("Storage target has no credentialRef configured");
        }
        OrgStorageCredential credential =
                repository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(organisation.getId(), credentialRef);
        if (credential == null) {
            throw new StorageConfigurationException(String.format(
                    "No storage credential found for credentialRef '%s' in organisation '%s'",
                    credentialRef, organisation.getName()));
        }
        return new StorageTargetCredentials(credential.getAccessKey(), decrypt(credential.getEncryptedSecretKey(), credentialRef));
    }

    @Override
    public long credentialVersion(Organisation organisation, String credentialRef) {
        if (organisation == null || !StringUtils.hasText(credentialRef)) {
            return 0L;
        }
        OrgStorageCredential credential =
                repository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(organisation.getId(), credentialRef);
        if (credential == null || credential.getLastModifiedDateTime() == null) {
            return 0L;
        }
        return credential.getLastModifiedDateTime().getMillis();
    }

    /**
     * Encrypts a secret for write. Used by the ops/admin write path (and tests) to populate the store.
     */
    public String encryptSecret(String plaintextSecret) throws GeneralSecurityException {
        requireMasterKey();
        byte[] encrypted = cryptoService.encryptWithIVPrefixed(plaintextSecret.getBytes(StandardCharsets.UTF_8), base64MasterKey);
        return cryptoService.encodeToBase64(encrypted);
    }

    /**
     * Lazy, fail-loud guard (avniproject/avni-server#1012, D14): the master key is intentionally
     * unset by default so the server boots fine for deployments that don't use per-org storage
     * routing. It is only required at the point an org storage credential is actually
     * encrypted/decrypted - at which point a blank key is a hard misconfiguration.
     */
    private void requireMasterKey() {
        if (!StringUtils.hasText(base64MasterKey)) {
            throw new StorageConfigurationException(
                    "storage credential master key (OPENCHS_STORAGE_CREDENTIALS_KEY) is not configured "
                            + "but an org storage credential was requested");
        }
    }

    private String decrypt(String encryptedSecretKey, String credentialRef) {
        requireMasterKey();
        try {
            return new String(
                    cryptoService.decryptWithIVPrefixed(cryptoService.decodeFromBase64(encryptedSecretKey), base64MasterKey),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Missing/incorrect deploy master key (or corrupt ciphertext). Fail loud.
            throw new StorageConfigurationException(String.format(
                    "Failed to decrypt storage credential for credentialRef '%s'; check the deploy master key "
                            + "'avni.storage.credentials.base64EncodedEncryptionKey'", credentialRef), e);
        }
    }
}
