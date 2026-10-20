package org.avni.server.service.abha;

public class EkaAuthException extends RuntimeException {
    public EkaAuthException(String message) {
        super(message);
    }

    public EkaAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
