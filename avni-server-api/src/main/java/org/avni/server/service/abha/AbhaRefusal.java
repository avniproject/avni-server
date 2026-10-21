package org.avni.server.service.abha;

// Why the ABHA screen was refused. The name is the code the app reads.
public enum AbhaRefusal {
    ABHA_DISABLED,
    ABHA_UNKNOWN_SUBJECT_TYPE,
    ABHA_NO_PRIVILEGE,
    ABHA_NOT_CONFIGURED,
    ABHA_NO_HEALTH_CENTRE,
    ABHA_EKA_UNAVAILABLE,
    ABHA_NUMBER_INVALID,
    // The card of a patient not on the server, asked for by someone who did not record their ABHA in the last 30 minutes.
    ABHA_NOT_YOURS,
    // No ABHA with an Eka ID is recorded for the patient, so there is no card to fetch.
    ABHA_NO_CARD,
    // The patient's ABHA login at Eka has lapsed, so Eka will not hand over the card until the ABHA is verified again.
    ABHA_SESSION_EXPIRED
}
