package org.avni.server.service.abdm.crypto;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

// DEMO-ONLY abdm-hackathon: never merged.
// ABDM's "Fidelius" scheme: ECDH on Curve25519 (BouncyCastle's Weierstrass form, not JDK X25519), HKDF-SHA256 with the
// first 20 bytes of the XOR of both nonces as salt, AES-256-GCM with the last 12 bytes as IV. Keys accepted as X.509 or
// raw point. Matches mgrmtech/fidelius-cli byte for byte (FideliusCryptoTest carries its README vector).
// Never logs a key, a nonce, a plaintext or a ciphertext; exception messages carry none of them either.
@Component
public class FideliusCrypto {
    private static final String CURVE = "curve25519";
    private static final String ALGORITHM = "ECDH";
    private static final String PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
    private static final int NONCE_BYTES = 32;
    private static final int SALT_BYTES = 20;
    private static final int IV_BYTES = 12;
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec CURVE_SPEC;

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        X9ECParameters p = CustomNamedCurves.getByName(CURVE);
        // Unnamed spec on purpose: the X.509 encoding then carries explicit curve parameters, as NHA's samples do.
        CURVE_SPEC = new ECParameterSpec(p.getCurve(), p.getG(), p.getN(), p.getH(), p.getSeed());
    }

    public static class FideliusCryptoException extends RuntimeException {
        public FideliusCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public FideliusKeyMaterial generateKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER);
            generator.initialize(CURVE_SPEC, RANDOM);
            KeyPair keyPair = generator.generateKeyPair();
            ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
            byte[] nonce = new byte[NONCE_BYTES];
            RANDOM.nextBytes(nonce);
            return new FideliusKeyMaterial(
                    b64(keyPair.getPrivate().getEncoded()),
                    b64(publicKey.getEncoded()),
                    b64(publicKey.getQ().getEncoded(false)),
                    b64(nonce));
        } catch (GeneralSecurityException e) {
            throw new FideliusCryptoException("Fidelius key material not generated", e);
        }
    }

    public String encrypt(String plaintext, String senderPrivateKey, String senderNonce, String requesterPublicKey, String requesterNonce) {
        byte[] secret = sharedSecret(parsePrivateKey(senderPrivateKey), parsePublicKey(requesterPublicKey));
        byte[] xor = xor(decode(senderNonce), decode(requesterNonce));
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, secret, xor);
            return b64(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new FideliusCryptoException("Fidelius encryption failed", e);
        }
    }

    public String decrypt(String cipherBase64, String requesterPrivateKey, String requesterNonce, String senderPublicKey, String senderNonce) {
        byte[] secret = sharedSecret(parsePrivateKey(requesterPrivateKey), parsePublicKey(senderPublicKey));
        byte[] xor = xor(decode(senderNonce), decode(requesterNonce));
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, secret, xor);
            return new String(cipher.doFinal(decode(cipherBase64)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException lands here: tampered data, the wrong key or the wrong nonce.
            throw new FideliusCryptoException("Fidelius decryption failed", e);
        }
    }

    static byte[] sharedSecret(PrivateKey privateKey, PublicKey publicKey) {
        try {
            KeyAgreement agreement = KeyAgreement.getInstance(ALGORITHM, PROVIDER);
            agreement.init(privateKey);
            agreement.doPhase(publicKey, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException | IllegalStateException e) {
            throw new FideliusCryptoException("Fidelius shared secret not computed", e);
        }
    }

    // A raw point (65 bytes, 0x04 first; or 33 bytes compressed) or an X.509 SubjectPublicKeyInfo.
    public static PublicKey parsePublicKey(String base64) {
        byte[] bytes = decode(base64);
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM, PROVIDER);
            boolean rawPoint = (bytes.length == 65 && bytes[0] == 0x04) || (bytes.length == 33 && (bytes[0] == 0x02 || bytes[0] == 0x03));
            if (rawPoint) {
                return factory.generatePublic(new ECPublicKeySpec(CURVE_SPEC.getCurve().decodePoint(bytes), CURVE_SPEC));
            }
            return factory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new FideliusCryptoException("Fidelius public key not readable", e);
        }
    }

    // A raw D (up to 32 bytes, or 33 with a leading zero as fidelius-cli writes it; shorter when D's top bytes are zero,
    // about one key in seventeen on this curve) or a PKCS#8 PrivateKeyInfo, which is always longer.
    public static PrivateKey parsePrivateKey(String base64) {
        byte[] bytes = decode(base64);
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM, PROVIDER);
            if (bytes.length > 0 && bytes.length <= 33) {
                return factory.generatePrivate(new ECPrivateKeySpec(new BigInteger(1, bytes), CURVE_SPEC));
            }
            return factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new FideliusCryptoException("Fidelius private key not readable", e);
        }
    }

    private static Cipher cipher(int mode, byte[] sharedSecret, byte[] nonceXor) throws GeneralSecurityException {
        byte[] salt = Arrays.copyOfRange(nonceXor, 0, SALT_BYTES);
        byte[] iv = Arrays.copyOfRange(nonceXor, nonceXor.length - IV_BYTES, nonceXor.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(aesKey(sharedSecret, salt), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    static byte[] aesKey(byte[] sharedSecret, byte[] salt) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(sharedSecret, salt, null));
        byte[] key = new byte[AES_KEY_BYTES];
        hkdf.generateBytes(key, 0, AES_KEY_BYTES);
        return key;
    }

    // As fidelius-cli: the first array's length, the second repeated if shorter. Both are 32 bytes in practice.
    static byte[] xor(byte[] a, byte[] b) {
        if (a.length < SALT_BYTES || b.length == 0) {
            throw new FideliusCryptoException("Fidelius nonce too short", null);
        }
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i % b.length]);
        }
        return out;
    }

    private static byte[] decode(String base64) {
        if (base64 == null) throw new FideliusCryptoException("Fidelius input missing", null);
        String clean = base64.replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(clean);
            } catch (IllegalArgumentException e2) {
                throw new FideliusCryptoException("Fidelius input is not base64", e2);
            }
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
