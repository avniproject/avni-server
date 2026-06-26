package org.avni.server.service;

import org.avni.server.dao.ModelKeyRepository;
import org.avni.server.domain.ModelKey;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.BadRequestError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.UUID;
import java.util.regex.Pattern;

// Server-only store of the model's AES key, encrypted at rest, keyed by org + sha256. The read path
// returns the REAL (unmasked) key (the device needs it to decrypt the model), unlike Msg91 which masks.
@Service
public class ModelKeyService {

    private static final Logger logger = LoggerFactory.getLogger(ModelKeyService.class);

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final ModelKeyRepository modelKeyRepository;
    private final CryptoService cryptoService;
    private final String base64MasterKey;

    @Autowired
    public ModelKeyService(ModelKeyRepository modelKeyRepository,
                           CryptoService cryptoService,
                           @Value("${avni.model.key.base64EncodedEncryptionKey}") String base64MasterKey) {
        this.modelKeyRepository = modelKeyRepository;
        this.cryptoService = cryptoService;
        this.base64MasterKey = base64MasterKey;
    }

    public ModelKey storeKey(String sha256, String plaintextBase64Key) {
        validateSha256(sha256);
        if (!StringUtils.hasText(plaintextBase64Key)) {
            throw new BadRequestError("Cannot store a blank model key");
        }
        Long organisationId = UserContextHolder.getUserContext().getOrganisation().getId();
        ModelKey modelKey = modelKeyRepository.findByOrganisationIdAndSha256AndIsVoidedFalse(organisationId, sha256);
        if (modelKey == null) {
            modelKey = new ModelKey();
            modelKey.setUuid(UUID.randomUUID().toString());
            modelKey.setSha256(sha256);
        }
        modelKey.setEncryptedKey(encrypt(plaintextBase64Key, sha256));
        return modelKeyRepository.save(modelKey);
    }

    // Returns null when no key exists, which the caller turns into a clean 404.
    public String getDecryptedKey(String sha256) {
        validateSha256(sha256);
        Long organisationId = UserContextHolder.getUserContext().getOrganisation().getId();
        ModelKey modelKey = modelKeyRepository.findByOrganisationIdAndSha256AndIsVoidedFalse(organisationId, sha256);
        if (modelKey == null) {
            return null;
        }
        return decrypt(modelKey.getEncryptedKey(), sha256);
    }

    // The raw client-supplied value is logged server-side but never echoed to the caller.
    private void validateSha256(String sha256) {
        if (!StringUtils.hasText(sha256)) {
            throw new BadRequestError("A model key sha256 is required");
        }
        if (!SHA256_HEX.matcher(sha256).matches()) {
            logger.warn("Rejected model key request with malformed sha256: '{}'", sha256);
            throw new BadRequestError("The provided sha256 is malformed; it must be a 64-character hex digest");
        }
    }

    private String encrypt(String plaintextBase64Key, String sha256) {
        requireMasterKey();
        try {
            byte[] encrypted = cryptoService.encryptWithIVPrefixed(plaintextBase64Key.getBytes(StandardCharsets.UTF_8), base64MasterKey);
            return cryptoService.encodeToBase64(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            logger.error("Failed to encrypt model key for sha256 '{}'; check the deploy master key "
                    + "'avni.model.key.base64EncodedEncryptionKey'", sha256, e);
            throw new ModelKeyException("Failed to encrypt the model key", e);
        }
    }

    private String decrypt(String encryptedKey, String sha256) {
        requireMasterKey();
        try {
            return new String(
                    cryptoService.decryptWithIVPrefixed(cryptoService.decodeFromBase64(encryptedKey), base64MasterKey),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            logger.error("Failed to decrypt model key for sha256 '{}'; check the deploy master key "
                    + "'avni.model.key.base64EncodedEncryptionKey'", sha256, e);
            throw new ModelKeyException("Failed to decrypt the model key", e);
        }
    }

    // Master key is unset by default (server boots fine without edge models); required only at point of use.
    private void requireMasterKey() {
        if (!StringUtils.hasText(base64MasterKey)) {
            throw new ModelKeyException(
                    "model key master key (OPENCHS_MODEL_KEY_ENCRYPTION_KEY) is not configured "
                            + "but a model key was requested");
        }
    }
}
