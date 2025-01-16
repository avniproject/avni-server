package org.avni.messaging.domain.exception;

public class GlificContactNotFoundError extends RuntimeException {
    public GlificContactNotFoundError(String msg) {
        super(msg);
    }
}
