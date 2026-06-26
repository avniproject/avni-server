package org.avni.server.service;

public class ModelKeyException extends RuntimeException {
    public ModelKeyException(String message) {
        super(message);
    }

    public ModelKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
