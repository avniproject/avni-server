package org.avni.server.service.abdm.hiu;

import org.avni.server.service.CryptoService;
import org.junit.Test;

import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

// DEMO-ONLY abdm-hackathon: records received from other providers are kept encrypted, under a key of their own (AVNI-14).
public class AbdmRecordCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    public void aRecordComesBackAsItWentIn_andIsNotReadableAtRest() {
        AbdmRecordCipher cipher = new AbdmRecordCipher(new CryptoService(), KEY);

        String stored = cipher.encrypt("{\"resourceType\":\"Bundle\"}");

        assertFalse(stored.contains("Bundle"));
        assertEquals("{\"resourceType\":\"Bundle\"}", cipher.decrypt(stored));
    }

    @Test
    public void withoutItsKeyNothingIsStored() {
        AbdmRecordCipher cipher = new AbdmRecordCipher(new CryptoService(), "");

        assertThrows(AbdmRecordCipher.NoKeyException.class, () -> cipher.encrypt("{}"));
    }
}
