package org.avni.server.service.abdm.eka;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka refused or did not answer. abdmCode is ABDM's own error code (e.g. ABDM-1035) when Eka's body named one.
// The message never carries a token, a secret or a patient's details.
public class EkaCallException extends RuntimeException {
    private final int status;
    private final String abdmCode;
    private boolean earlierTryMayHaveActed;

    public EkaCallException(int status, String abdmCode, String message) {
        super(message);
        this.status = status;
        this.abdmCode = abdmCode;
    }

    public int getStatus() {
        return status;
    }

    public String getAbdmCode() {
        return abdmCode;
    }

    // Set by EkaAbdmClient when an earlier try of the same call may have reached Eka and been acted on.
    EkaCallException afterATryEkaMayHaveActedOn() {
        earlierTryMayHaveActed = true;
        return this;
    }

    boolean earlierTryMayHaveActed() {
        return earlierTryMayHaveActed;
    }
}
