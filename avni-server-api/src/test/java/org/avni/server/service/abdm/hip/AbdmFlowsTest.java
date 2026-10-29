package org.avni.server.service.abdm.hip;

import org.junit.Test;

import static org.junit.Assert.*;

// DEMO-ONLY abdm-hackathon: Eka's free-text errors can quote the patient (AVNI-10). Whatever reaches our tables, logs
// and screens loses the identifiers in it, and keeps what support needs to trace the call.
public class AbdmFlowsTest {
    @Test
    public void masksEveryKindOfIdentifierInFreeText() {
        String masked = AbdmFlows.maskIdentifiers("Patient 91-1234-5678-9012 / 91123456789012, Aadhaar 1234 5678 9012, mobile "
                + "+91 9876543210, 98765 43210 and 9876543210, address Asha.Rao@SBX");

        for (String identifier : new String[]{"91-1234-5678-9012", "91123456789012", "1234 5678 9012", "9876543210",
                "98765 43210", "Asha.Rao@SBX"}) {
            assertFalse(masked, masked.contains(identifier));
        }
        assertTrue(masked, masked.contains("***"));
        assertTrue(masked, masked.startsWith("Patient "));
    }

    @Test
    public void keepsWhatSupportNeeds() {
        String text = "ABDM-1035 at 2026-10-29T11:21:22.695Z request 5fc3880a-28d1-46c0-badb-123456789012 epoch 1793253081000 HTTP 491";

        assertEquals(text, AbdmFlows.maskIdentifiers(text));
        assertNull(AbdmFlows.maskIdentifiers(null));
    }
}
