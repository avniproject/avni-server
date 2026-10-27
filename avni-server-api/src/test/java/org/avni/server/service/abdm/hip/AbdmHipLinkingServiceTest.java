package org.avni.server.service.abdm.hip;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Gender;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmConfig;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.avni.server.service.abdm.hip.AbdmTestSupport.HIP_ID;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbdmHipLinkingServiceTest {
    private static final DateTimeZone IST = DateTimeZone.forID("Asia/Kolkata");
    private static final long INDIVIDUAL_ID = 7L;
    private static final String DIGITS = "12345678568912";

    private final AbdmHipStore store = mock(AbdmHipStore.class);
    private final IndividualRepository individualRepository = mock(IndividualRepository.class);
    private final AbhaLinkRepository abhaLinkRepository = mock(AbhaLinkRepository.class);
    private AbdmConfig cfg;
    private AbdmHipLinkingService service;

    @Before
    public void setUp() {
        cfg = AbdmTestSupport.config();
        service = new AbdmHipLinkingService(cfg, store, AbdmTestSupport.orgContext(), individualRepository, abhaLinkRepository);
        Individual individual = individual();
        when(individualRepository.findByUuid("subject-1")).thenReturn(individual);
        AbhaLink link = new AbhaLink();
        link.setAbhaNumber(DIGITS);
        link.setAbhaAddress("asha.devi");
        when(abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(INDIVIDUAL_ID)).thenReturn(Optional.of(link));
        when(store.findCareContexts(INDIVIDUAL_ID, HIP_ID)).thenReturn(List.of(row("enc-1", "PENDING", null), row("enc-2", "PENDING", null)));
    }

    @Test
    public void programmeVisitsAreVisitsToo_newestFirst_withoutThoseOfAVoidedEnrolment() {
        Individual patient = new Individual();
        org.avni.server.domain.Encounter general = new org.avni.server.domain.Encounter();
        general.setUuid("enc-1");
        general.setEncounterType(encounterType("OPD Visit"));
        general.setEncounterDateTime(new org.joda.time.DateTime(2026, 3, 14, 10, 0), null);
        patient.setEncounters(new java.util.HashSet<>(List.of(general)));
        org.avni.server.domain.ProgramEnrolment live = enrolment(patient, false, programVisit("penc-1", "HTN Follow-up", new org.joda.time.DateTime(2026, 10, 26, 9, 0)));
        org.avni.server.domain.ProgramEnrolment voided = enrolment(patient, true, programVisit("penc-2", "HTN Follow-up", new org.joda.time.DateTime(2026, 10, 1, 9, 0)));
        patient.setProgramEnrolments(new java.util.HashSet<>(List.of(live, voided)));

        List<AbdmHipLinkingService.Visit> visits = AbdmHipLinkingService.visits(patient);

        assertEquals(List.of("penc-1", "enc-1"), visits.stream().map(AbdmHipLinkingService.Visit::reference).toList());
        assertEquals("HTN Follow-up, 26 Oct 2026", visits.get(0).display());
    }

    private static org.avni.server.domain.EncounterType encounterType(String name) {
        org.avni.server.domain.EncounterType t = new org.avni.server.domain.EncounterType();
        t.setName(name);
        return t;
    }

    private static org.avni.server.domain.ProgramEncounter programVisit(String uuid, String type, org.joda.time.DateTime at) {
        org.avni.server.domain.ProgramEncounter v = new org.avni.server.domain.ProgramEncounter();
        v.setUuid(uuid);
        v.setEncounterType(encounterType(type));
        v.setEncounterDateTime(at, null);
        return v;
    }

    private static org.avni.server.domain.ProgramEnrolment enrolment(Individual patient, boolean voided, org.avni.server.domain.ProgramEncounter visit) {
        org.avni.server.domain.ProgramEnrolment e = new org.avni.server.domain.ProgramEnrolment();
        e.setIndividual(patient);
        e.setVoided(voided);
        visit.setProgramEnrolment(e);
        e.setProgramEncounters(new java.util.HashSet<>(List.of(visit)));
        return e;
    }

    @Test
    public void careContextsAreTheCompletedVisitsNewestFirstWithTypeAndDate() {
        List<AbdmHipLinkingService.Visit> visits = service.loadPatient("subject-1").visits();

        assertEquals(2, visits.size());
        assertEquals("enc-2", visits.get(0).reference());
        assertEquals("OPD Visit, 2 May 2026", visits.get(0).display());
        assertEquals("enc-1", visits.get(1).reference());
        assertEquals("OPD Visit, 14 Mar 2026", visits.get(1).display());
    }

    @Test
    public void patientsShowAMaskedNumberAndTheAddressWithItsSuffix() {
        when(store.patientsWithAbha(HIP_ID)).thenReturn(List.of(new AbdmHipStore.PatientRow("subject-1", "Asha Devi", DIGITS, "asha.devi", 3, 1)));

        Map<String, Object> p = service.patientsWithAbha().get(0);

        assertEquals("subject-1", p.get("subjectUUID"));
        assertEquals("xx-xxxx-xxxx-8912", p.get("abhaNumberMasked"));
        assertEquals("asha.devi@sbx", p.get("abhaAddress"));
        assertEquals(3L, p.get("encounters"));
        assertEquals(1L, p.get("linked"));
    }

    private static AbdmHipStore.CareContext row(String reference, String status, String linkRequestId) {
        return new AbdmHipStore.CareContext(1, "cc-" + reference, INDIVIDUAL_ID, HIP_ID, "subject-1", reference, "Display " + reference,
                "OPConsultation", status, "HIP", linkRequestId, null, Instant.parse("2026-10-25T10:00:00Z"));
    }

    private static Individual individual() {
        Individual individual = new Individual();
        individual.setId(INDIVIDUAL_ID);
        individual.setUuid("subject-1");
        individual.setFirstName("Asha");
        individual.setLastName("Devi");
        Gender gender = new Gender();
        gender.setName("Female");
        individual.setGender(gender);
        individual.setDateOfBirth(new LocalDate(1990, 5, 1));
        EncounterType opd = new EncounterType();
        opd.setName("OPD Visit");
        Set<Encounter> encounters = new HashSet<>();
        encounters.add(encounter("enc-1", opd, new DateTime(2026, 3, 14, 10, 0, IST), null, false));
        encounters.add(encounter("enc-2", opd, new DateTime(2026, 5, 2, 23, 30, IST), null, false));
        encounters.add(encounter("enc-scheduled", opd, null, null, false));
        encounters.add(encounter("enc-cancelled", opd, new DateTime(2026, 4, 1, 10, 0, IST), new DateTime(2026, 4, 1, 11, 0, IST), false));
        encounters.add(encounter("enc-voided", opd, new DateTime(2026, 4, 2, 10, 0, IST), null, true));
        individual.setEncounters(encounters);
        return individual;
    }

    private static Encounter encounter(String uuid, EncounterType type, DateTime at, DateTime cancelledAt, boolean voided) {
        Encounter e = new Encounter();
        e.setUuid(uuid);
        e.setEncounterType(type);
        e.setEncounterDateTime(at, null);
        e.setCancelDateTime(cancelledAt);
        e.setVoided(voided);
        return e;
    }
}
