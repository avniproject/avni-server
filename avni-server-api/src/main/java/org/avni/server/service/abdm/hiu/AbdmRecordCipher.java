package org.avni.server.service.abdm.hiu;

import org.avni.server.service.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

// DEMO-ONLY abdm-hackathon: never merged.
// Health records received from other providers are kept encrypted (AVNI-14), under a deploy key of their own,
// abdm.records.base64EncodedEncryptionKey (ABDM_RECORDS_KEY): not the Eka credentials key. Without it nothing is stored.
@Component
public class AbdmRecordCipher {
    static final String KEY_PROPERTY = "abdm.records.base64EncodedEncryptionKey";

    public static class NoKeyException extends IllegalStateException {
        NoKeyException(String message) {
            super(message);
        }
    }

    private final CryptoService cryptoService;
    private final String base64Key;

    @Autowired
    public AbdmRecordCipher(CryptoService cryptoService, @Value("${abdm.records.base64EncodedEncryptionKey:}") String base64Key) {
        this.cryptoService = cryptoService;
        this.base64Key = base64Key;
    }

    public String encrypt(String plaintext) {
        requireKey();
        try {
            return cryptoService.encodeToBase64(cryptoService.encryptWithIVPrefixed(plaintext.getBytes(StandardCharsets.UTF_8), base64Key));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("A received record could not be encrypted; check " + KEY_PROPERTY, e);
        }
    }

    public String decrypt(String stored) {
        requireKey();
        try {
            return new String(cryptoService.decryptWithIVPrefixed(cryptoService.decodeFromBase64(stored), base64Key), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("A received record could not be decrypted; check " + KEY_PROPERTY, e);
        }
    }

    private void requireKey() {
        if (!StringUtils.hasText(base64Key)) {
            throw new NoKeyException("No key for received health records: set " + KEY_PROPERTY + " (ABDM_RECORDS_KEY)");
        }
    }
}
