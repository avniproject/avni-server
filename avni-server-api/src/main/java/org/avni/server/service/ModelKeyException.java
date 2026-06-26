package org.avni.server.service;

/**
 * Thrown by the server-only model key store (avniproject/avni-server#1020) when the AES key cannot be
 * encrypted/decrypted - typically a missing/incorrect deploy master key. The key store must fail
 * <b>loud</b> at point-of-use rather than return garbage that would only surface as a GCM tag failure
 * on-device after a ~68 MB model download + decrypt.
 */
public class ModelKeyException extends RuntimeException {
    public ModelKeyException(String message) {
        super(message);
    }

    public ModelKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
