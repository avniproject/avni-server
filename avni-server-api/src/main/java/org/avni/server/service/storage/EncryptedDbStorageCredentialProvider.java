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

// Reads a target's credential from the encrypted-per-org DB store, decrypting the secret under a deploy master key.
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

    public String encryptSecret(String plaintextSecret) throws GeneralSecurityException {
        requireMasterKey();
        byte[] encrypted = cryptoService.encryptWithIVPrefixed(plaintextSecret.getBytes(StandardCharsets.UTF_8), base64MasterKey);
        return cryptoService.encodeToBase64(encrypted);
    }

    // Master key is unset by default (server boots fine without per-org storage routing); required only at point of use.
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
            throw new StorageConfigurationException(String.format(
                    "Failed to decrypt storage credential for credentialRef '%s'; check the deploy master key "
                            + "'avni.storage.credentials.base64EncodedEncryptionKey'", credentialRef), e);
        }
    }
}
