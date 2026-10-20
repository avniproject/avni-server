package org.avni.server.service.abha;

import org.avni.server.service.CryptoService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EkaCredentialCipherTest {

    private static final String KEY = "W+6wNuSHHVCnO6tqN2EK5CE0kRa+0mcn/fNmDifSkEU=";
    private static final String OTHER_KEY = "xqtzQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";
    private static final String PROPERTY = "avni.eka.base64EncodedEncryptionKey";

    private final CryptoService cryptoService = new CryptoService();

    @Test
    public void encryptThenDecryptReturnsPlaintext() {
        EkaCredentialCipher cipher = new EkaCredentialCipher(cryptoService, KEY);
        assertEquals("eka-client-secret", cipher.decrypt(cipher.encrypt("eka-client-secret")));
    }

    @Test
    public void ciphertextDoesNotContainPlaintext() {
        EkaCredentialCipher cipher = new EkaCredentialCipher(cryptoService, KEY);
        String encrypted = cipher.encrypt("eka-client-secret");
        assertFalse(encrypted.contains("eka-client-secret"));
        assertFalse(new String(cryptoService.decodeFromBase64(encrypted)).contains("eka-client-secret"));
    }

    @Test
    public void blankKeyThrowsNamingProperty() {
        EkaCredentialCipher cipher = new EkaCredentialCipher(cryptoService, "");
        try {
            cipher.encrypt("eka-client-secret");
            fail("encrypting without a key must fail");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(PROPERTY));
        }
    }

    @Test
    public void decryptWithOtherKeyThrowsNamingProperty() {
        String encrypted = new EkaCredentialCipher(cryptoService, KEY).encrypt("eka-client-secret");
        EkaCredentialCipher otherKeyCipher = new EkaCredentialCipher(cryptoService, OTHER_KEY);
        try {
            otherKeyCipher.decrypt(encrypted);
            fail("decrypting with another key must fail");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(PROPERTY));
            assertFalse(e.getMessage(), e.getMessage().contains("eka-client-secret"));
        }
    }
}
