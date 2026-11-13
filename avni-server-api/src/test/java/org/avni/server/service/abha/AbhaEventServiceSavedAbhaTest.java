package org.avni.server.service.abha;

import org.avni.server.domain.AbhaEvent;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

// The Eka record of the ABHA a patient's saved record holds (AbhaEventService.ofSavedAbha), which the
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

    // A record the phone sent with the saved number and someone else's address is not the saved ABHA's: its address and
    // Eka ID would bind the patient's visits to that other person.
    @Test
    public void aRecordWithTheSavedNumberButAnotherAddressIsNotTheSavedAbhas() {
        AbhaEvent otherAddress = record("linked", "oid-other", SAVED, "someone.else@sbx");
        AbhaEvent saved = record("created", "oid-saved", SAVED, "patient@sbx");

        assertEquals(Optional.empty(), AbhaEventService.ofSavedAbha(List.of(otherAddress), SAVED, "patient@sbx"));
        assertEquals(Optional.of(saved), AbhaEventService.ofSavedAbha(List.of(otherAddress, saved), SAVED, "patient@sbx"));
    }

    // As AbdmFlows.namesAbha compares: in any case, with any suffix, or the saved address being the number's default one.
    @Test
    public void theAddressAgreesInAnyCaseWithAnySuffix_orAsTheNumbersDefaultAddress() {
        AbhaEvent saved = record("linked", "oid-saved", SAVED, "patient@sbx");

        assertEquals(Optional.of(saved), AbhaEventService.ofSavedAbha(List.of(saved), SAVED, "Patient@abdm"));
        assertEquals(Optional.of(saved), AbhaEventService.ofSavedAbha(List.of(saved), SAVED, SAVED + "@sbx"));
        assertEquals(Optional.of(saved), AbhaEventService.ofSavedAbha(List.of(saved), null, SAVED + "@abdm"));
    }

    // Once a record has given the saved ABHA an Eka ID, a later record naming another Eka ID for it is the phone's word
    // alone: the first stands, and its latest record is the one used.
    @Test
    public void aLaterRecordNamingAnotherEkaIdForTheSavedAbhaDoesNotReplaceTheFirst() {
        AbhaEvent first = record("linked", "oid-first", SAVED, "patient@sbx");
        AbhaEvent another = record("verified", "oid-another", SAVED, "patient@sbx");
        AbhaEvent firstAgain = record("verified", "oid-first", SAVED, "patient@sbx");

        assertEquals(Optional.of(first), AbhaEventService.ofSavedAbha(List.of(another, first), SAVED, "patient@sbx"));
        assertEquals(Optional.of(firstAgain), AbhaEventService.ofSavedAbha(List.of(firstAgain, another, first), SAVED, "patient@sbx"));
    }

    // A removal ends the earlier claim: an ABHA added again may come with another Eka ID.
    @Test
    public void afterARemovalTheNextEkaIdIsTheSavedAbhas() {
        AbhaEvent before = record("linked", "oid-before", SAVED, "patient@sbx");
        AbhaEvent removed = record("removed", null, null, null);
        AbhaEvent after = record("linked", "oid-after", SAVED, "patient@sbx");

        assertEquals(Optional.of(after), AbhaEventService.ofSavedAbha(List.of(after, removed, before), SAVED, "patient@sbx"));
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
