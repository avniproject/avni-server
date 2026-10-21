package org.avni.server.service.abha;

// Eka refused a patient's ABHA card because the patient's ABHA login at Eka has lapsed. Eka keeps that login for about
// 30 minutes after the patient's ABHA was found, created or verified, and answers its own HTTP 491 after it. Only
// verifying the ABHA again brings the card back; trying again does not.
public class EkaSessionExpiredException extends EkaCardException {
    public EkaSessionExpiredException(String message) {
        super(message);
    }
}
