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

/**
 * Server-only model key store (avniproject/avni-server#1020, D19/D20).
 * <p>
 * Stores the edge model's AES key {@link ModelKey#getEncryptedKey() encrypted at rest} (via
 * {@link CryptoService}, AES/GCM, IV-prefixed) under a base64 deploy master key, keyed by org +
 * {@code sha256}. Mirrors the {@code Msg91Config} / {@code OrgStorageCredential} (story-3 cred store)
 * precedent.
 * <p>
 * <b>Deliberate Msg91 deviation (D19):</b> Msg91's read path masks the secret; this store's
 * {@link #getDecryptedKey(String) read path returns the REAL (unmasked) key}, because the device must
 * use the actual bytes to decrypt the model. The key is never written to any subject/ref-data record or
 * any {@code /web/...} response - it is served only by the device key-delivery endpoint.
 * <p>
 * <b>No client gate (D20):</b> "not surfaced to web/DEA" is an exposure reduction, <b>not</b>
 * cryptographic android-only access - Avni has no client-type gate, so any authenticated {@code 'user'}
 * could call the endpoint.
 * <p>
 * <b>Master-key posture (F2 lesson):</b> the master key is intentionally empty by default so the server
 * boots fine when per-org models aren't in use. It is required (and validated, fail-loud) only at
 * point-of-use (encrypt/decrypt) - exactly like {@code EncryptedDbStorageCredentialProvider.requireMasterKey()}.
 */
@Service
public class ModelKeyService {

    private static final Logger logger = LoggerFactory.getLogger(ModelKeyService.class);

    /** A SHA-256 hex digest is exactly 64 hex characters. Validate before any lookup/echo. */
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

    /**
     * Ops/admin write path: stores (encrypting on write) the AES key for a given {@code sha256} in the
     * current org. The plaintext key is never persisted. Upserts on (org, sha256).
     */
    public ModelKey storeKey(String sha256, String plaintextBase64Key) {
        // Client input validation -> 400 (BadRequestError, the repo standard), NOT a 500/Bugsnag.
        // The sha256 is client-supplied; validate its shape and never echo the raw value to the caller.
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

    /**
     * Device read path: returns the REAL (unmasked) AES key for a {@code sha256} in the current org, or
     * {@code null} if no key exists (the caller turns that into a clean 404, not a 500). Deliberately
     * does NOT mask (D19) - the device needs the actual key bytes to decrypt the model.
     */
    public String getDecryptedKey(String sha256) {
        // Validate shape before any lookup: a blank or malformed (non-64-hex) sha256 is a client error
        // -> clean 400 (BadRequestError), never a verbatim lookup and never echoed back to the caller.
        validateSha256(sha256);
        Long organisationId = UserContextHolder.getUserContext().getOrganisation().getId();
        ModelKey modelKey = modelKeyRepository.findByOrganisationIdAndSha256AndIsVoidedFalse(organisationId, sha256);
        if (modelKey == null) {
            return null;
        }
        return decrypt(modelKey.getEncryptedKey(), sha256);
    }

    /**
     * Validates that {@code sha256} is present and a 64-char hex SHA-256 digest. A blank or malformed
     * value is a client error -> {@link BadRequestError} (HTTP 400). The raw client-supplied value is
     * never interpolated into the message returned to the caller (it is logged server-side instead).
     */
    private void validateSha256(String sha256) {
        if (!StringUtils.hasText(sha256)) {
            throw new BadRequestError("A model key sha256 is required");
        }
        if (!SHA256_HEX.matcher(sha256).matches()) {
            // Log the offending value server-side for diagnosis; do NOT echo it to the client.
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
            // Log the real detail (incl. sha256 + master-key prop) server-side; the @ExceptionHandler
            // returns a generic message to the caller so no config/property name is leaked.
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
            // Missing/incorrect deploy master key (or corrupt ciphertext). Fail loud, but only in the
            // server log: the @ExceptionHandler returns a generic message so the master-key prop name,
            // internal config and the raw sha256 are never echoed to the caller.
            logger.error("Failed to decrypt model key for sha256 '{}'; check the deploy master key "
                    + "'avni.model.key.base64EncodedEncryptionKey'", sha256, e);
            throw new ModelKeyException("Failed to decrypt the model key", e);
        }
    }

    /**
     * Lazy, fail-loud guard (F2 lesson from story 3): the master key is intentionally unset by default so
     * the server boots fine for deployments that don't use edge models. It is only required at the point
     * a model key is actually encrypted/decrypted - at which point a blank key is a hard misconfiguration.
     * Mirrors {@code EncryptedDbStorageCredentialProvider.requireMasterKey()}.
     */
    private void requireMasterKey() {
        if (!StringUtils.hasText(base64MasterKey)) {
            throw new ModelKeyException(
                    "model key master key (OPENCHS_MODEL_KEY_ENCRYPTION_KEY) is not configured "
                            + "but a model key was requested");
        }
    }
}
