package org.avni.server.service.abdm.crypto;

import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// ABDM's "Fidelius" scheme: ECDH on Curve25519 (BouncyCastle's Weierstrass form, not JDK X25519), HKDF-SHA256 with the
// first 20 bytes of the XOR of both nonces as salt, AES-256-GCM with the last 12 bytes as IV. Keys accepted as X.509 or
// raw point.
@Component
public class FideliusCrypto {
    public FideliusKeyMaterial generateKeyMaterial() {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public String encrypt(String plaintext, String senderPrivateKey, String senderNonce, String requesterPublicKey, String requesterNonce) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public String decrypt(String cipherBase64, String requesterPrivateKey, String requesterNonce, String senderPublicKey, String senderNonce) {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}
