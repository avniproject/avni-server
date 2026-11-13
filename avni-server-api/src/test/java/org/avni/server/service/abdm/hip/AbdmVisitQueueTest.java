package org.avni.server.service.abdm.hip;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.service.abdm.AbdmSettings;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSettings;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Which visits are queued and under which HIP ID, with no transaction; AbdmSaveHooksIntegrationTest covers the after-commit write.
public class AbdmVisitQueueTest {
    private final AbdmSettings settings = AbdmTestSupport.settings();
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbhaLinkRepository abhaLinks = mock(AbhaLinkRepository.class);
    private final org.avni.server.dao.AbhaEventRepository abhaEvents = mock(org.avni.server.dao.AbhaEventRepository.class);
    private final org.avni.server.service.abha.AbhaVerifiedRecords verifiedRecords =
            new org.avni.server.service.abha.AbhaVerifiedRecords(abhaEvents, mock(com.bugsnag.Bugsnag.class));
    private Individual patient;

    @Before
    public void setUp() {
        patient = new Individual();
        patient.setId(7L);
        patient.setUuid("subject-1");
        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.of(new AbhaLink()));
    }

    @Test
    public void aCompletedVisitOfAPatientWithAnAbhaIsQueuedForLinking() {
        queue().queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));

        verify(store).queueCareContexts(7L, HIP_ID, "subject-1", "OPConsultation",
                List.of(new AbdmHipStore.NewCareContext("enc-9", "OPD Visit, 2 May 2026")));
    }

    @Test
    public void aCompletedProgramVisitIsQueuedToo() {
        ProgramEnrolment enrolment = new ProgramEnrolment();
        enrolment.setIndividual(patient);
        ProgramEncounter visit = new ProgramEncounter();
        visit.setUuid("penc-1");
        visit.setProgramEnrolment(enrolment);
        visit.setEncounterType(type("Hypertension Follow-up"));
        visit.setEncounterDateTime(new DateTime(2026, 10, 26, 9, 0), null);

        queue().queue(visit);

        verify(store).queueCareContexts(7L, HIP_ID, "subject-1", "OPConsultation",
                List.of(new AbdmHipStore.NewCareContext("penc-1", "Hypertension Follow-up, 26 Oct 2026")));
    }

    @Test
    public void aVisitOfAPatientWithNoAbhaIsNotQueued() {
        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.empty());

        queue().queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));

        verify(store, never()).queueCareContexts(anyLong(), any(), any(), any(), any());
    }

    @Test
    public void aVisitNotYetDoneCancelledOrVoidedIsNotQueued() {
        Encounter planned = visit("enc-1", null);
        Encounter cancelled = visit("enc-2", new DateTime(2026, 5, 2, 10, 0));
        cancelled.setCancelDateTime(new DateTime(2026, 5, 2, 11, 0));
        Encounter voided = visit("enc-3", new DateTime(2026, 5, 2, 10, 0));
        voided.setVoided(true);

        AbdmVisitQueue q = queue();
        q.queue(planned);
        q.queue(cancelled);
        q.queue(voided);

        verify(store, never()).queueCareContexts(anyLong(), any(), any(), any(), any());
    }

    // F4: a save that voids or cancels a visit gives up its waiting care context, under whichever of the organisation's HIP
    // IDs it was made; a visit not yet done has none to give up.
    @Test
    public void aVisitVoidedOrCancelledHasItsWaitingCareContextGivenUp() {
        Encounter planned = visit("enc-1", null);
        Encounter cancelled = visit("enc-2", new DateTime(2026, 5, 2, 10, 0));
        cancelled.setCancelDateTime(new DateTime(2026, 5, 2, 11, 0));
        Encounter voided = visit("enc-3", new DateTime(2026, 5, 2, 10, 0));
        voided.setVoided(true);

        AbdmVisitQueue q = new AbdmVisitQueue(AbdmTestSupport.settings(twoHips()), store, abhaLinks, verifiedRecords);
        q.queue(planned);
        q.queue(cancelled);
        q.queue(voided);

        verify(store).giveUpRemovedVisits(List.of("hip-1", "hip-2"), List.of("enc-2"));
        verify(store).giveUpRemovedVisits(List.of("hip-1", "hip-2"), List.of("enc-3"));
        verify(store, never()).giveUpRemovedVisits(any(), eq(List.of("enc-1")));
        org.mockito.Mockito.verifyNoInteractions(abhaLinks);
    }

    @Test
    public void aFailureToGiveUpARemovedVisitNeverFailsTheSave() {
        Encounter voided = visit("enc-3", new DateTime(2026, 5, 2, 10, 0));
        voided.setVoided(true);
        doThrow(new IllegalStateException("table missing")).when(store).giveUpRemovedVisits(any(), any());

        queue().queue(voided);
    }

    @Test
    public void aFailureToQueueNeverFailsTheSave() {
        doThrow(new IllegalStateException("table missing")).when(store).queueCareContexts(anyLong(), any(), any(), any(), any());

        queue().queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));
    }

    // Avni starts this link: the visit goes under the HIP ID of the user who recorded it, when it is the organisation's.
    @Test
    public void aVisitIsQueuedUnderTheHipIdOfTheUserWhoRecordedIt() {
        Encounter visit = visit("enc-9", new DateTime(2026, 5, 2, 10, 0));
        visit.setCreatedBy(userAt("hip-2"));

        new AbdmVisitQueue(AbdmTestSupport.settings(twoHips()), store, abhaLinks, verifiedRecords).queue(visit);

        verify(store).queueCareContexts(7L, "hip-2", "subject-1", "OPConsultation",
                List.of(new AbdmHipStore.NewCareContext("enc-9", "OPD Visit, 2 May 2026")));
    }

    @Test
    public void aVisitRecordedByAUserOfNoneOfTheOrganisationsHipIdsIsQueuedUnderItsFirst() {
        Encounter elsewhere = visit("enc-8", new DateTime(2026, 5, 2, 10, 0));
        elsewhere.setCreatedBy(userAt("someone-elses-hip"));
        Encounter unknown = visit("enc-9", new DateTime(2026, 5, 2, 10, 0));
        AbdmVisitQueue q = new AbdmVisitQueue(AbdmTestSupport.settings(twoHips()), store, abhaLinks, verifiedRecords);

        q.queue(elsewhere);
        q.queue(unknown);

        verify(store).queueCareContexts(7L, "hip-1", "subject-1", "OPConsultation", List.of(new AbdmHipStore.NewCareContext("enc-8", "OPD Visit, 2 May 2026")));
        verify(store).queueCareContexts(7L, "hip-1", "subject-1", "OPConsultation", List.of(new AbdmHipStore.NewCareContext("enc-9", "OPD Visit, 2 May 2026")));
    }

    // A visit whose care context exists is never queued under a second HIP ID, which would link it twice.
    @Test
    public void aVisitWithACareContextKeepsTheHipIdItWasMadeUnder() {
        Encounter visit = visit("enc-9", new DateTime(2026, 5, 2, 10, 0));
        visit.setCreatedBy(userAt("hip-2"));
        when(store.findCareContextsByReferences("hip-1", List.of("enc-9"))).thenReturn(List.of(new AbdmHipStore.CareContext(1, "cc-1", 7L,
                "hip-1", "subject-1", "enc-9", "OPD Visit, 2 May 2026", "OPConsultation", "LINKED", "HIP", null, null, null)));

        new AbdmVisitQueue(AbdmTestSupport.settings(twoHips()), store, abhaLinks, verifiedRecords).queue(visit);

        verify(store).queueCareContexts(eq(7L), eq("hip-1"), any(), any(), any());
        verify(store, never()).queueCareContexts(anyLong(), eq("hip-2"), any(), any(), any());
    }

    @Test
    public void anOrganisationWithoutEkaSettingsQueuesNothing() {
        AbdmSettings none = org.mockito.Mockito.mock(AbdmSettings.class);
        when(none.find()).thenReturn(Optional.empty());

        new AbdmVisitQueue(none, store, abhaLinks, verifiedRecords).queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));

        verify(store, never()).queueCareContexts(anyLong(), any(), any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(abhaLinks);
    }

    private static EkaSystemConfig twoHips() {
        return AbdmTestSupport.ekaSettings(new JsonObject().with(EkaSystemConfig.HIP_IDS, List.of("hip-1", "hip-2")));
    }

    private static User userAt(String hipId) {
        User user = new User();
        user.setSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, hipId));
        return user;
    }

    private AbdmVisitQueue queue() {
        return new AbdmVisitQueue(settings, store, abhaLinks, verifiedRecords);
    }

    // ---- a saved subject whose ABHA is verified

    @Test
    public void aSavedSubjectWhoseAbhaIsVerifiedHasItsWaitingVisitsQueuedAgain() {
        holds("12345678568913", "oid-verified-1");

        queue().requeueIfAbhaVerified(patient);

        verify(store).requeueForVerifiedAbha("subject-1");
    }

    // The record still holds a typed ABHA no record verifies, or none: nothing would link yet, so nothing is queued.
    @Test
    public void aSavedSubjectWhoseAbhaIsNotVerifiedQueuesNothing() {
        holds("12345678568913", null);
        queue().requeueIfAbhaVerified(patient);

        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.empty());
        queue().requeueIfAbhaVerified(patient);

        verify(store, never()).requeueForVerifiedAbha(any());
    }

    // ---- a record the phone sends for a patient

    @Test
    public void aRecordOfTheSavedAbhaQueuesItsWaitingVisitsAgain() {
        holds("12345678568913", "patient@sbx", "oid-verified-1", "patient@sbx");

        queue().requeueForVerifiedAbha("subject-1");

        verify(store).requeueForVerifiedAbha("subject-1");
    }

    // The phone's record names the saved number with another person's address: it verifies nothing, so nothing is queued,
    // whether the record or the patient's form arrives last.
    @Test
    public void aRecordWithTheSavedNumberButAnotherAddressQueuesNothing() {
        holds("12345678568913", "patient@sbx", "oid-verified-1", "someone.else@sbx");

        queue().requeueForVerifiedAbha("subject-1");
        queue().requeueIfAbhaVerified(patient);

        verify(store, never()).requeueForVerifiedAbha(any());
    }

    @Test
    public void aRecordForAPatientHoldingNoAbhaQueuesNothing() {
        holds("12345678568913", "patient@sbx", "oid-verified-1", "patient@sbx");
        when(abhaLinks.findByIndividualUuidAndIsVoidedFalse("subject-1")).thenReturn(Optional.empty());

        queue().requeueForVerifiedAbha("subject-1");

        verify(store, never()).requeueForVerifiedAbha(any());
    }

    private void holds(String abhaNumber, String ekaOid) {
        holds(abhaNumber, null, ekaOid, null);
    }

    private void holds(String abhaNumber, String savedAddress, String ekaOid, String recordAddress) {
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber(abhaNumber);
        link.setAbhaAddress(savedAddress);
        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.of(link));
        when(abhaLinks.findByIndividualUuidAndIsVoidedFalse("subject-1")).thenReturn(Optional.of(link));
        org.avni.server.domain.AbhaEvent record = new org.avni.server.domain.AbhaEvent();
        record.setAction("verified");
        record.setAbhaNumber(abhaNumber);
        record.setAbhaAddress(recordAddress);
        record.setEkaOid(ekaOid);
        when(abhaEvents.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of(record));
    }

    private Encounter visit(String uuid, DateTime at) {
        Encounter e = new Encounter();
        e.setUuid(uuid);
        e.setIndividual(patient);
        e.setEncounterType(type("OPD Visit"));
        if (at != null) e.setEncounterDateTime(at, null);
        return e;
    }

    private static EncounterType type(String name) {
        EncounterType t = new EncounterType();
        t.setName(name);
        return t;
    }
}
