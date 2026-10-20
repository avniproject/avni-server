package org.avni.server.service.abha;

public class AbhaRefusedException extends RuntimeException {
    private final AbhaRefusal refusal;

    public AbhaRefusedException(AbhaRefusal refusal) {
        super(refusal.name());
        this.refusal = refusal;
    }

    public AbhaRefusal getRefusal() {
        return refusal;
    }
}
