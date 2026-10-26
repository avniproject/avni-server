package org.avni.server.service.abdm.crypto;

import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.junit.Test;

import java.security.PrivateKey;
import java.util.Base64;

import static org.junit.Assert.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class FideliusCryptoTest {
    private static final String PLAINTEXT = "{\"resourceType\":\"Bundle\",\"note\":\"ರಕ್ತದೊತ್ತಡ 130/85 — ok\"}";

    // Test vector from mgrmtech/fidelius-cli README (main branch, fetched 25 Oct 2026): a Java/BouncyCastle
    // implementation of the same scheme, independent of this one.
    private static final String FC_REQUESTER_PRIVATE = "DMxHPri8d7IT23KgLk281zZenMfVHSdeamq0RhwlIBk=";
    private static final String FC_REQUESTER_PUBLIC = "BAheD5rUqTy4V5xR4/6HWmYpopu5CO+KO8BECS0udNqUTSNo91TIqIIy1A4Vh+F94c+n9vAcwXU2bGcfsI5f69Y=";
    private static final String FC_REQUESTER_X509 = "MIIBMTCB6gYHKoZIzj0CATCB3gIBATArBgcqhkjOPQEBAiB/////////////////////////////////////////7TBEBCAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqYSRShRAQge0Je0Je0Je0Je0Je0Je0Je0Je0Je0Je0JgtenHcQyGQEQQQqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq0kWiCuGaG4oIa04B7dLHdI0UySPU1+bXxhsinpxaJ+ztPZAiAQAAAAAAAAAAAAAAAAAAAAFN753qL3nNZYEmMaXPXT7QIBCANCAAQIXg+a1Kk8uFecUeP+h1pmKaKbuQjvijvARAktLnTalE0jaPdUyKiCMtQOFYfhfeHPp/bwHMF1NmxnH7COX+vW";
    private static final String FC_REQUESTER_NONCE = "6uj1RdDUbcpI3lVMZvijkMC8Te20O4Bcyz0SyivX8Eg=";
    private static final String FC_SENDER_PRIVATE = "AYhVZpbVeX4KS5Qm/W0+9Ye2q3rnVVGmqRICmseWni4=";
    private static final String FC_SENDER_PUBLIC = "BABVt+mpRLMXiQpIfEq6bj8hlXsdtXIxLsspmMgLNI1SR5mHgDVbjHO2A+U4QlMddGzqyEidzm1AkhtSxSO2Ahg=";
    private static final String FC_SENDER_X509 = "MIIBMTCB6gYHKoZIzj0CATCB3gIBATArBgcqhkjOPQEBAiB/////////////////////////////////////////7TBEBCAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqYSRShRAQge0Je0Je0Je0Je0Je0Je0Je0Je0Je0Je0JgtenHcQyGQEQQQqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq0kWiCuGaG4oIa04B7dLHdI0UySPU1+bXxhsinpxaJ+ztPZAiAQAAAAAAAAAAAAAAAAAAAAFN753qL3nNZYEmMaXPXT7QIBCANCAAQAVbfpqUSzF4kKSHxKum4/IZV7HbVyMS7LKZjICzSNUkeZh4A1W4xztgPlOEJTHXRs6shInc5tQJIbUsUjtgIY";
    private static final String FC_SENDER_NONCE = "lmXgblZwotx+DfBgKJF0lZXtAXgBEYr5khh79Zytr2Y=";
    private static final String FC_PLAINTEXT = "Wormtail should never have been Potter cottage's secret keeper.";
    private static final String FC_CIPHER = "pzMvVZNNVtJzqPkkxcCbBUWgDEBy/mBXIeT2dJWI16ZAQnnXUb9lI+S4k8XK6mgZSKKSRIHkcNvJpllnBg548wUgavBa0vCRRwdL6kY6Yw==";

    private final FideliusCrypto crypto = new FideliusCrypto();

    @Test
    public void hipEncryptsAndHiuDecrypts() {
        FideliusKeyMaterial hiu = crypto.generateKeyMaterial();
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();

        String cipher = crypto.encrypt(PLAINTEXT, hip.privateKeyBase64(), hip.nonceBase64(), hiu.publicKeyX509Base64(), hiu.nonceBase64());

        assertNotEquals(PLAINTEXT, cipher);
        assertEquals(PLAINTEXT, crypto.decrypt(cipher, hiu.privateKeyBase64(), hiu.nonceBase64(), hip.publicKeyX509Base64(), hip.nonceBase64()));
    }

    @Test
    public void rawPointAndX509FormsOfTheSameKeyGiveTheSameResult() {
        FideliusKeyMaterial hiu = crypto.generateKeyMaterial();
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();

        String viaX509 = crypto.encrypt(PLAINTEXT, hip.privateKeyBase64(), hip.nonceBase64(), hiu.publicKeyX509Base64(), hiu.nonceBase64());
        String viaRaw = crypto.encrypt(PLAINTEXT, hip.privateKeyBase64(), hip.nonceBase64(), hiu.publicKeyRawBase64(), hiu.nonceBase64());

        assertEquals(viaX509, viaRaw);
        assertEquals(PLAINTEXT, crypto.decrypt(viaRaw, hiu.privateKeyBase64(), hiu.nonceBase64(), hip.publicKeyRawBase64(), hip.nonceBase64()));
        assertEquals(PLAINTEXT, crypto.decrypt(viaRaw, hiu.privateKeyBase64(), hiu.nonceBase64(), hip.publicKeyX509Base64(), hip.nonceBase64()));
    }

    @Test(expected = FideliusCrypto.FideliusCryptoException.class)
    public void tamperedCiphertextIsRefused() {
        FideliusKeyMaterial hiu = crypto.generateKeyMaterial();
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String cipher = crypto.encrypt(PLAINTEXT, hip.privateKeyBase64(), hip.nonceBase64(), hiu.publicKeyX509Base64(), hiu.nonceBase64());
        byte[] bytes = Base64.getDecoder().decode(cipher);
        bytes[3] ^= 0x01;

        crypto.decrypt(Base64.getEncoder().encodeToString(bytes), hiu.privateKeyBase64(), hiu.nonceBase64(), hip.publicKeyX509Base64(), hip.nonceBase64());
    }

    @Test(expected = FideliusCrypto.FideliusCryptoException.class)
    public void wrongNonceIsRefused() {
        FideliusKeyMaterial hiu = crypto.generateKeyMaterial();
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String cipher = crypto.encrypt(PLAINTEXT, hip.privateKeyBase64(), hip.nonceBase64(), hiu.publicKeyX509Base64(), hiu.nonceBase64());

        crypto.decrypt(cipher, hiu.privateKeyBase64(), crypto.generateKeyMaterial().nonceBase64(), hip.publicKeyX509Base64(), hip.nonceBase64());
    }

    @Test
    public void sharedSecretIsTheSameFromEitherSideAnd32Bytes() {
        FideliusKeyMaterial a = crypto.generateKeyMaterial();
        FideliusKeyMaterial b = crypto.generateKeyMaterial();

        byte[] ab = FideliusCrypto.sharedSecret(FideliusCrypto.parsePrivateKey(a.privateKeyBase64()), FideliusCrypto.parsePublicKey(b.publicKeyX509Base64()));
        byte[] ba = FideliusCrypto.sharedSecret(FideliusCrypto.parsePrivateKey(b.privateKeyBase64()), FideliusCrypto.parsePublicKey(a.publicKeyRawBase64()));

        assertEquals(32, ab.length);
        assertArrayEquals(ab, ba);
    }

    @Test
    public void keyMaterialHasA32ByteNonceAndAnUncompressedRawPoint() {
        FideliusKeyMaterial km = crypto.generateKeyMaterial();

        assertEquals(32, Base64.getDecoder().decode(km.nonceBase64()).length);
        byte[] raw = Base64.getDecoder().decode(km.publicKeyRawBase64());
        assertEquals(65, raw.length);
        assertEquals(0x04, raw[0]);
        assertTrue(km.publicKeyRawBase64().startsWith("B"));
        assertNotEquals(km.nonceBase64(), crypto.generateKeyMaterial().nonceBase64());
    }

    @Test
    public void ourX509CarriesTheSameExplicitCurveParametersAsFideliusCli() {
        String ours = crypto.generateKeyMaterial().publicKeyX509Base64();
        // Everything before the point (the last 65 bytes = 88 base64 chars, aligned at 3 bytes) is algorithm + curve.
        String header = FC_SENDER_X509.substring(0, FC_SENDER_X509.length() - 88);
        assertEquals(FC_SENDER_X509.length(), ours.length());
        assertEquals(header, ours.substring(0, header.length()));
    }

    @Test
    public void rawPrivateKeyAndPkcs8FormsAgree() {
        FideliusKeyMaterial a = crypto.generateKeyMaterial();
        FideliusKeyMaterial b = crypto.generateKeyMaterial();
        PrivateKey pkcs8 = FideliusCrypto.parsePrivateKey(a.privateKeyBase64());
        // fidelius-cli's form: D as a signed big-endian integer, 32 or 33 bytes.
        String rawD = Base64.getEncoder().encodeToString(((ECPrivateKey) pkcs8).getD().toByteArray());

        String fromPkcs8 = crypto.encrypt(PLAINTEXT, a.privateKeyBase64(), a.nonceBase64(), b.publicKeyRawBase64(), b.nonceBase64());
        String fromRaw = crypto.encrypt(PLAINTEXT, rawD, a.nonceBase64(), b.publicKeyRawBase64(), b.nonceBase64());

        assertEquals(fromPkcs8, fromRaw);
    }

    // About one key in seventeen has a D whose shortest form is 31 bytes; a raw key that short was refused.
    @Test
    public void aRawPrivateKeyShorterThan32BytesIsRead() {
        FideliusKeyMaterial b = crypto.generateKeyMaterial();
        for (int tries = 0; tries < 2000; tries++) {
            FideliusKeyMaterial a = crypto.generateKeyMaterial();
            byte[] d = ((ECPrivateKey) FideliusCrypto.parsePrivateKey(a.privateKeyBase64())).getD().toByteArray();
            if (d.length >= 32) continue;
            String rawD = Base64.getEncoder().encodeToString(d);
            assertEquals(crypto.encrypt(PLAINTEXT, a.privateKeyBase64(), a.nonceBase64(), b.publicKeyRawBase64(), b.nonceBase64()),
                    crypto.encrypt(PLAINTEXT, rawD, a.nonceBase64(), b.publicKeyRawBase64(), b.nonceBase64()));
            return;
        }
        throw new AssertionError("no short key in 2000 tries");
    }

    @Test
    public void decryptsFideliusCliVector() {
        assertEquals(FC_PLAINTEXT, crypto.decrypt(FC_CIPHER, FC_REQUESTER_PRIVATE, FC_REQUESTER_NONCE, FC_SENDER_PUBLIC, FC_SENDER_NONCE));
        assertEquals(FC_PLAINTEXT, crypto.decrypt(FC_CIPHER, FC_REQUESTER_PRIVATE, FC_REQUESTER_NONCE, FC_SENDER_X509, FC_SENDER_NONCE));
    }

    @Test
    public void encryptsFideliusCliVectorToTheSameCiphertext() {
        assertEquals(FC_CIPHER, crypto.encrypt(FC_PLAINTEXT, FC_SENDER_PRIVATE, FC_SENDER_NONCE, FC_REQUESTER_PUBLIC, FC_REQUESTER_NONCE));
        assertEquals(FC_CIPHER, crypto.encrypt(FC_PLAINTEXT, FC_SENDER_PRIVATE, FC_SENDER_NONCE, FC_REQUESTER_X509, FC_REQUESTER_NONCE));
    }

    @Test
    public void ciphertextIsPlaintextPlusA16ByteTag() {
        FideliusKeyMaterial hiu = crypto.generateKeyMaterial();
        FideliusKeyMaterial hip = crypto.generateKeyMaterial();
        String cipher = crypto.encrypt("abc", hip.privateKeyBase64(), hip.nonceBase64(), hiu.publicKeyRawBase64(), hiu.nonceBase64());
        assertEquals(3 + 16, Base64.getDecoder().decode(cipher).length);
    }
}
