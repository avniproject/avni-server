package org.avni.server.service.abha;

// An ABHA event the server cannot keep as sent. The message names the field, never its value.
public class AbhaEventInvalidException extends RuntimeException {
    public AbhaEventInvalidException(String message) {
        super(message);
    }
}
