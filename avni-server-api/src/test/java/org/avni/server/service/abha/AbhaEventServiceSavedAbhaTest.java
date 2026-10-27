package org.avni.server.service.abha;

import org.avni.server.domain.AbhaEvent;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

// DEMO-ONLY abdm: the Eka record of the ABHA a patient's saved record holds (AbhaEventService.ofSavedAbha), which the
// link to Eka and the consent request use. Records are newest first, as the repository returns them.
public class AbhaEventServiceSavedAbhaTest {
    private static final String SAVED = "91111122222009";

    @Test
    public void theSavedAbhasRecordIsTakenOverALaterLookupOfAnother() {
        AbhaEvent other = record("linked", "oid-other", "91333344444533", "someone.else@sbx");
        AbhaEvent saved = record("created", "oid-saved", SAVED, "patient@sbx");

        assertEquals(Optional.of(saved), AbhaEventService.ofSavedAbha(List.of(other, saved), SAVED, "patient@sbx"));
    }

    @Test
    public void theNumberDecidesWhenBothHaveOne_theAddressOnlyWhenOneHasNone() {
        AbhaEvent sameAddressOtherNumber = record("linked", "oid-x", "91000000000001", "patient@sbx");
        AbhaEvent addressOnly = record("created", "oid-address", null, "Patient@abdm");

        assertEquals(Optional.of(addressOnly), AbhaEventService.ofSavedAbha(List.of(sameAddressOtherNumber, addressOnly), SAVED, "patient@sbx"));
        assertEquals(Optional.of(addressOnly), AbhaEventService.ofSavedAbha(List.of(addressOnly), null, "patient"));
    }

    @Test
    public void aRecordWithNoEkaIdIsPassedOver_andARemovalEndsTheSearch() {
        AbhaEvent noEkaId = record("verified", null, SAVED, "patient@sbx");
        AbhaEvent saved = record("created", "oid-saved", SAVED, "patient@sbx");
        AbhaEvent removed = record("removed", null, null, null);

        assertEquals(Optional.of(saved), AbhaEventService.ofSavedAbha(List.of(noEkaId, saved), SAVED, null));
        assertEquals(Optional.empty(), AbhaEventService.ofSavedAbha(List.of(removed, saved), SAVED, "patient@sbx"));
    }

    @Test
    public void aRecordWithNoAbhaSavedHasNoEkaRecord() {
        AbhaEvent saved = record("created", "oid-saved", SAVED, "patient@sbx");

        assertEquals(Optional.empty(), AbhaEventService.ofSavedAbha(List.of(saved), null, null));
        assertEquals(Optional.empty(), AbhaEventService.ofSavedAbha(List.of(saved), "1234", " "));
    }

    private static AbhaEvent record(String action, String oid, String number, String address) {
        AbhaEvent event = new AbhaEvent();
        event.setAction(action);
        event.setEkaOid(oid);
        event.setAbhaNumber(number);
        event.setAbhaAddress(address);
        return event;
    }
}
