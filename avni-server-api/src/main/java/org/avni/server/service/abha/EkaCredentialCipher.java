package org.avni.server.service.abha;

import org.avni.server.service.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

// Encrypts the Eka client secret and webhook signing key at rest, under a deploy key of their own.
@Service
public class EkaCredentialCipher {

    private static final Logger logger = LoggerFactory.getLogger(EkaCredentialCipher.class);

    private static final String KEY_PROPERTY = "avni.eka.base64EncodedEncryptionKey";

    private final CryptoService cryptoService;
    private final String base64MasterKey;

    @Autowired
    public EkaCredentialCipher(CryptoService cryptoService,
                               @Value("${avni.eka.base64EncodedEncryptionKey}") String base64MasterKey) {
        this.cryptoService = cryptoService;
        this.base64MasterKey = base64MasterKey;
    }

    public String encrypt(String plaintext) {
        requireMasterKey();
        try {
            byte[] encrypted = cryptoService.encryptWithIVPrefixed(plaintext.getBytes(StandardCharsets.UTF_8), base64MasterKey);
            return cryptoService.encodeToBase64(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            logger.error("Failed to encrypt an Eka secret; check the deploy key '{}'", KEY_PROPERTY, e);
            throw new IllegalStateException("Failed to encrypt an Eka secret; check the deploy key '" + KEY_PROPERTY + "'", e);
        }
    }

    public String decrypt(String encrypted) {
        requireMasterKey();
        try {
            return new String(
                    cryptoService.decryptWithIVPrefixed(cryptoService.decodeFromBase64(encrypted), base64MasterKey),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            logger.error("Failed to decrypt an Eka secret; check the deploy key '{}'", KEY_PROPERTY, e);
            throw new IllegalStateException("Failed to decrypt an Eka secret; check the deploy key '" + KEY_PROPERTY + "'", e);
        }
    }

    // Unset by default (the server boots without Eka); required only at point of use.
    private void requireMasterKey() {
        if (!StringUtils.hasText(base64MasterKey)) {
            throw new IllegalStateException(
                    "Eka credentials key '" + KEY_PROPERTY + "' (OPENCHS_EKA_CREDENTIALS_KEY) is not configured "
                            + "but an Eka secret was requested");
        }
    }
}
