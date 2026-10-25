package org.avni.server.service.abdm.gateway;

// DEMO-ONLY abdm-hackathon: never merged.
// The gateway refused or did not answer. abdmCode is ABDM's own error code (e.g. ABDM-1035) when its body named one.
// The message never carries a token, a secret or a patient's details.
public class AbdmGatewayException extends RuntimeException {
    private final int status;
    private final String abdmCode;

    public AbdmGatewayException(int status, String abdmCode, String message) {
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
}
