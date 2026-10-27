package org.avni.server.service.abdm;

// DEMO-ONLY abdm-hackathon: never merged.
public final class AbdmPaths {
    private AbdmPaths() {
    }

    // Eka Care posts every ABDM event (link status, data fetch, consent update, data push, patient-led linking) here,
    // signed. The only ABDM path open to the internet (ResourceProtectionStatus).
    public static final String IN_EKA_WEBHOOK = "/api/v3/eka/webhook";
}
