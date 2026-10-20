package org.avni.server.service.abha;

// Eka did not hand over a patient's ABHA card: it refused, did not answer in time, or sent something other than the card.
// The message never carries the access token or the patient's Eka ID.
public class EkaCardException extends RuntimeException {
    public EkaCardException(String message) {
        super(message);
    }

    public EkaCardException(String message, Throwable cause) {
        super(message, cause);
    }
}
