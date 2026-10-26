package org.avni.server.service.abdm.hip;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.service.abdm.AbdmConfig;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: HIP_INTI_LINK_505, "linking of health records should be done automatically once records have
// been saved at HIP end". A saved visit is queued here, inside the save; AbdmHousekeepingJob links the queue.
public class AbdmVisitQueueTest {
    private final AbdmConfig cfg = AbdmTestSupport.config();
    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final AbhaLinkRepository abhaLinks = mock(AbhaLinkRepository.class);
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
        queue(true).queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));

        verify(store).ensureCareContexts(7L, HIP_ID, "subject-1", "OPConsultation",
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

        queue(true).queue(visit);

        verify(store).ensureCareContexts(7L, HIP_ID, "subject-1", "OPConsultation",
                List.of(new AbdmHipStore.NewCareContext("penc-1", "Hypertension Follow-up, 26 Oct 2026")));
    }

    @Test
    public void aVisitOfAPatientWithNoAbhaIsNotQueued() {
        when(abhaLinks.findByIndividualIdAndIsVoidedFalse(7L)).thenReturn(Optional.empty());

        queue(true).queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));

        verify(store, never()).ensureCareContexts(anyLong(), any(), any(), any(), any());
    }

    @Test
    public void aVisitNotYetDoneCancelledOrVoidedIsNotQueued() {
        Encounter planned = visit("enc-1", null);
        Encounter cancelled = visit("enc-2", new DateTime(2026, 5, 2, 10, 0));
        cancelled.setCancelDateTime(new DateTime(2026, 5, 2, 11, 0));
        Encounter voided = visit("enc-3", new DateTime(2026, 5, 2, 10, 0));
        voided.setVoided(true);

        AbdmVisitQueue q = queue(true);
        q.queue(planned);
        q.queue(cancelled);
        q.queue(voided);

        verify(store, never()).ensureCareContexts(anyLong(), any(), any(), any(), any());
    }

    @Test
    public void aFailureToQueueNeverFailsTheSave() {
        doThrow(new IllegalStateException("table missing")).when(store).ensureCareContexts(anyLong(), any(), any(), any(), any());

        queue(true).queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));
    }

    @Test
    public void switchedOffNothingIsQueued() {
        queue(false).queue(visit("enc-9", new DateTime(2026, 5, 2, 10, 0)));

        verify(store, never()).ensureCareContexts(anyLong(), any(), any(), any(), any());
    }

    private AbdmVisitQueue queue(boolean on) {
        return new AbdmVisitQueue(cfg, store, abhaLinks, on);
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
