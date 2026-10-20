package org.avni.server.service.abha;

// Why the ABHA screen was refused. The name is the code the app reads.
public enum AbhaRefusal {
    ABHA_DISABLED,
    ABHA_UNKNOWN_SUBJECT_TYPE,
    ABHA_NO_PRIVILEGE,
    ABHA_NOT_CONFIGURED,
    ABHA_NO_HEALTH_CENTRE
}
