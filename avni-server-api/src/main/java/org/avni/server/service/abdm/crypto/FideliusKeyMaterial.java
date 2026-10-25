package org.avni.server.service.abdm.crypto;

// DEMO-ONLY abdm-hackathon: never merged.
// One side's key material for a health-information transfer. All base64. publicKeyX509Base64 is what we send;
// publicKeyRawBase64 is the 65-byte uncompressed point (starts "B"), the form NHA's samples show.
public record FideliusKeyMaterial(String privateKeyBase64, String publicKeyX509Base64, String publicKeyRawBase64, String nonceBase64) {
}
