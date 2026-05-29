package org.avni.server.service.attendance;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.AttendanceStatus;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.attendance.SessionStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.EncounterService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.attendance.AttendanceRecordContract;
import org.avni.server.web.request.attendance.SessionContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private AttendanceTypeRepository attendanceTypeRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private CalendarRepository calendarRepository;
    @Mock
    private CalendarDateMarkerRepository calendarDateMarkerRepository;
    @Mock
    private EncounterTypeRepository encounterTypeRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private EncounterService encounterService;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;

    private SessionService service;

    private Individual groupSubject;
    private AttendanceType attendanceType;

    @Before
    public void setUp() {
        initMocks(this);
        service = new SessionService(sessionRepository, attendanceRecordRepository, individualRepository,
                attendanceTypeRepository, conceptRepository, calendarRepository, calendarDateMarkerRepository,
                encounterTypeRepository, encounterRepository, encounterService, subjectTypeRepository);

        User user = new User();
        user.setUuid("user-uuid");
        UserContext ctx = new UserContext();
        ctx.setUser(user);
        UserContextHolder.create(ctx);

        groupSubject = new Individual();
        groupSubject.setUuid("group-uuid");
        attendanceType = new AttendanceType();
        attendanceType.setUuid("att-type-uuid");

        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attendanceRecordRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(Collections.emptyList());
        when(attendanceRecordRepository.findBySessionAndIsVoidedFalse(any())).thenReturn(Collections.emptyList());
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void heldSessionPersistsSessionAndRoster() {
        Individual subject1 = subjectWithUuid("subj-1");
        Individual subject2 = subjectWithUuid("subj-2");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(individualRepository.findByUuid("subj-2")).thenReturn(subject2);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(conceptRepository.findAllByUuidIn(anyList())).thenReturn(Collections.emptyList());

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setRoster(List.of(
                rosterEntry("subj-1", AttendanceStatus.Present),
                rosterEntry("subj-2", AttendanceStatus.Absent)));

        SessionSaveResult result = service.save(contract);

        verify(sessionRepository, times(1)).save(any(Session.class));
        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRecordRepository, times(1)).saveAll(captor.capture());
        List<AttendanceRecord> persisted = captor.getValue();
        assertEquals(2, persisted.size());
        assertSame(subject1, persisted.get(0).getSubject());
        assertEquals(AttendanceStatus.Present, persisted.get(0).getStatus());
        assertSame(subject2, persisted.get(1).getSubject());
        assertEquals(AttendanceStatus.Absent, persisted.get(1).getStatus());
        assertEquals(SessionStatus.Held, result.getSession().getStatus());
        assertEquals(2, result.getAttendanceRecords().size());
        assertTrue(result.getAutoCreatedFollowUps().isEmpty());
    }

    @Test
    public void didntHappenSessionSkipsRosterPersist() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        Concept reason = conceptWithUuid("reason-uuid");
        when(conceptRepository.findByUuid("reason-uuid")).thenReturn(reason);

        SessionContract contract = baseContract(SessionStatus.DidntHappen);
        contract.setReasonConceptUUID("reason-uuid");
        contract.setRoster(List.of(rosterEntry("subj-1", AttendanceStatus.Present)));

        SessionSaveResult result = service.save(contract);

        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
        assertTrue(result.getAttendanceRecords().isEmpty());
    }

    @Test
    public void missingGroupSubjectThrowsBeforeAnyPersist() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(null);

        try {
            service.save(baseContract(SessionStatus.Held));
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("group-uuid"));
        }
        verify(sessionRepository, never()).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
    }

    @Test
    public void missingAttendanceTypeThrowsBeforeAnyPersist() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(null);

        try {
            service.save(baseContract(SessionStatus.Held));
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("att-type-uuid"));
        }
        verify(sessionRepository, never()).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
    }

    @Test
    public void rosterWithMissingSubjectAbortsBeforeRosterPersist() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subjectWithUuid("subj-1"));
        when(individualRepository.findByUuid("subj-bad")).thenReturn(null);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(conceptRepository.findAllByUuidIn(anyList())).thenReturn(Collections.emptyList());

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setRoster(List.of(
                rosterEntry("subj-1", AttendanceStatus.Present),
                rosterEntry("subj-bad", AttendanceStatus.Absent)));

        try {
            service.save(contract);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("subj-bad"));
        }
        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
    }

    @Test
    public void rosterReasonConceptsBatchLoadedOnce() {
        Individual subject1 = subjectWithUuid("subj-1");
        Individual subject2 = subjectWithUuid("subj-2");
        Concept reason = conceptWithUuid("reason-uuid");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(individualRepository.findByUuid("subj-2")).thenReturn(subject2);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(conceptRepository.findAllByUuidIn(anyList())).thenReturn(List.of(reason));

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract r1 = rosterEntry("subj-1", AttendanceStatus.Absent);
        r1.setReasonConceptUUID("reason-uuid");
        AttendanceRecordContract r2 = rosterEntry("subj-2", AttendanceStatus.Absent);
        r2.setReasonConceptUUID("reason-uuid");
        contract.setRoster(List.of(r1, r2));

        service.save(contract);

        verify(conceptRepository, times(1)).findAllByUuidIn(anyList());
        verify(conceptRepository, never()).findByUuid(any());
    }

    @Test
    public void legacySingleReasonFieldIsStoredAsOneElementArray() {
        Individual subject1 = subjectWithUuid("subj-1");
        Concept reason = conceptWithUuid("reason-uuid");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(conceptRepository.findAllByUuidIn(anyList())).thenReturn(List.of(reason));

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract legacy = rosterEntry("subj-1", AttendanceStatus.Absent);
        legacy.setReasonConceptUUID("reason-uuid"); // pre-16.15 single-field client
        contract.setRoster(List.of(legacy));

        service.save(contract);

        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRecordRepository, times(1)).saveAll(captor.capture());
        assertEquals(List.of("reason-uuid"), captor.getValue().get(0).getReasonConceptUUIDs());
    }

    @Test
    public void storesMultipleReasonsAndDropsUuidsThatAreNotConcepts() {
        Individual subject1 = subjectWithUuid("subj-1");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(conceptRepository.findAllByUuidIn(anyList()))
                .thenReturn(List.of(conceptWithUuid("reason-a"), conceptWithUuid("reason-b")));

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract record = rosterEntry("subj-1", AttendanceStatus.Absent);
        record.setReasonConceptUUIDs(List.of("reason-a", "reason-b", "not-a-concept"));
        contract.setRoster(List.of(record));

        service.save(contract);

        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRecordRepository, times(1)).saveAll(captor.capture());
        assertEquals(List.of("reason-a", "reason-b"), captor.getValue().get(0).getReasonConceptUUIDs());
    }

    @Test
    public void heldSessionWithEmptyRosterPersistsSessionOnly() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setRoster(Collections.emptyList());

        SessionSaveResult result = service.save(contract);

        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
        assertTrue(result.getAttendanceRecords().isEmpty());
    }

    @Test
    public void rejectsFutureScheduledDate() {
        SessionContract contract = baseContract(SessionStatus.Held);
        LocalDate future = LocalDate.now().plusDays(1);
        contract.setScheduledDate(future);

        try {
            service.save(contract);
            fail("Expected FutureScheduledDateNotAllowedException");
        } catch (FutureScheduledDateNotAllowedException e) {
            assertEquals(future, e.getScheduledDate());
        }
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    public void didntHappenWithoutReasonThrowsReasonRequired() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);

        SessionContract contract = baseContract(SessionStatus.DidntHappen);

        try {
            service.save(contract);
            fail("Expected ReasonRequiredException");
        } catch (ReasonRequiredException e) {
            assertEquals(ReasonRequiredException.RequiredFor.DidntHappen, e.getRequiredFor());
        }
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    public void autoCreatesFollowUpWhenAbsentAndNeedsFollowUpFlagged() {
        Individual subject1 = subjectWithUuid("subj-1");
        EncounterType followUpType = new EncounterType();
        followUpType.setUuid("followup-type-uuid");
        followUpType.setName("Home Visit");
        attendanceType.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "followup-type-uuid"));

        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(encounterTypeRepository.findByUuid("followup-type-uuid")).thenReturn(followUpType);
        when(encounterService.createEmptyEncounter(any(), any())).thenAnswer(inv -> {
            Encounter encounter = new Encounter();
            encounter.setIndividual(inv.getArgument(0));
            encounter.setEncounterType(inv.getArgument(1));
            return encounter;
        });

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract absent = rosterEntry("subj-1", AttendanceStatus.Absent);
        absent.setNeedsFollowUp(true);
        contract.setRoster(List.of(absent));

        SessionSaveResult result = service.save(contract);

        assertEquals(1, result.getAutoCreatedFollowUps().size());
        assertEquals("Home Visit", result.getAutoCreatedFollowUps().get(0).getEncounterTypeName());
        verify(encounterService, times(1)).save(any(Encounter.class));
    }

    @Test
    public void autoCreatesFollowUpWhenAbsentWithReasonAndNeedsFollowUpFlagged() {
        Individual subject1 = subjectWithUuid("subj-1");
        Concept reason = conceptWithUuid("reason-uuid");
        EncounterType followUpType = new EncounterType();
        followUpType.setUuid("followup-type-uuid");
        followUpType.setName("Home Visit");
        attendanceType.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "followup-type-uuid"));

        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(conceptRepository.findAllByUuidIn(anyList())).thenReturn(List.of(reason));
        when(encounterTypeRepository.findByUuid("followup-type-uuid")).thenReturn(followUpType);
        when(encounterService.createEmptyEncounter(any(), any())).thenAnswer(inv -> {
            Encounter encounter = new Encounter();
            encounter.setIndividual(inv.getArgument(0));
            encounter.setEncounterType(inv.getArgument(1));
            return encounter;
        });

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract absent = rosterEntry("subj-1", AttendanceStatus.Absent);
        absent.setReasonConceptUUID("reason-uuid");
        absent.setNeedsFollowUp(true);
        contract.setRoster(List.of(absent));

        SessionSaveResult result = service.save(contract);

        assertEquals(1, result.getAutoCreatedFollowUps().size());
        verify(encounterService, times(1)).save(any(Encounter.class));
    }

    @Test
    public void skipsFollowUpWhenAbsentButNeedsFollowUpNotFlagged() {
        Individual subject1 = subjectWithUuid("subj-1");
        EncounterType followUpType = new EncounterType();
        followUpType.setUuid("followup-type-uuid");
        followUpType.setName("Home Visit");
        attendanceType.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "followup-type-uuid"));

        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(encounterTypeRepository.findByUuid("followup-type-uuid")).thenReturn(followUpType);

        SessionContract contract = baseContract(SessionStatus.Held);
        // Absent with no reason and no needsFollowUp flag — under the new rule, no follow-up.
        contract.setRoster(List.of(rosterEntry("subj-1", AttendanceStatus.Absent)));

        SessionSaveResult result = service.save(contract);

        assertEquals(0, result.getAutoCreatedFollowUps().size());
        verify(encounterService, never()).save(any(Encounter.class));
    }

    @Test
    public void doesNotDoubleCreateWhenClientPreCreatedUuidExists() {
        Individual subject1 = subjectWithUuid("subj-1");
        EncounterType followUpType = new EncounterType();
        followUpType.setUuid("followup-type-uuid");
        followUpType.setName("Home Visit");
        attendanceType.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "followup-type-uuid"));

        Encounter preExisting = new Encounter();
        preExisting.setUuid("client-uuid");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(encounterTypeRepository.findByUuid("followup-type-uuid")).thenReturn(followUpType);
        when(encounterRepository.findAllByUuidIn(List.of("client-uuid"))).thenReturn(List.of(preExisting));

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract record = rosterEntry("subj-1", AttendanceStatus.Absent);
        record.setNeedsFollowUp(true);
        record.setFollowUpEncounterUUID("client-uuid");
        contract.setRoster(List.of(record));

        SessionSaveResult result = service.save(contract);

        verify(encounterService, never()).save(any(Encounter.class));
        assertEquals(0, result.getAutoCreatedFollowUps().size());
    }

    @Test
    public void doesNotDoubleCreateWhenExistingRecordCarriesFollowUpAndContractDoesNotEchoIt() {
        Individual subject1 = subjectWithUuid("subj-1");
        EncounterType followUpType = new EncounterType();
        followUpType.setUuid("followup-type-uuid");
        followUpType.setName("Home Visit");
        attendanceType.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "followup-type-uuid"));

        AttendanceRecord previousRecord = new AttendanceRecord();
        previousRecord.setUuid("record-uuid");
        previousRecord.setSubject(subject1);
        previousRecord.setStatus(AttendanceStatus.Absent);
        previousRecord.setNeedsFollowUp(true);
        previousRecord.setFollowUpEncounterUuid("enc-1");
        Encounter existingEncounter = new Encounter();
        existingEncounter.setUuid("enc-1");

        Session existingSession = new Session();
        existingSession.setUuid("session-uuid");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(encounterTypeRepository.findByUuid("followup-type-uuid")).thenReturn(followUpType);
        when(attendanceRecordRepository.findBySessionAndIsVoidedFalse(any())).thenReturn(List.of(previousRecord));
        when(encounterRepository.findAllByUuidIn(List.of("enc-1"))).thenReturn(List.of(existingEncounter));

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setUuid("session-uuid");
        AttendanceRecordContract stillAbsent = rosterEntry("subj-1", AttendanceStatus.Absent);
        stillAbsent.setUuid("record-uuid");
        stillAbsent.setNeedsFollowUp(true);
        contract.setRoster(List.of(stillAbsent));

        SessionSaveResult result = service.update(existingSession, contract);

        verify(encounterService, never()).save(any(Encounter.class));
        assertEquals(0, result.getAutoCreatedFollowUps().size());
        assertEquals(0, result.getVoidedStaleFollowUps().size());
    }

    @Test
    public void crossTypeStackingCreatesOneEncounterPerAttendanceType() {
        Individual subject1 = subjectWithUuid("subj-1");
        EncounterType homeVisit = new EncounterType();
        homeVisit.setUuid("home-visit-type");
        homeVisit.setName("Home Visit");
        EncounterType phoneCall = new EncounterType();
        phoneCall.setUuid("phone-call-type");
        phoneCall.setName("Phone Call");

        AttendanceType prayerType = new AttendanceType();
        prayerType.setUuid("prayer-type-uuid");
        prayerType.setConfig(new JsonObject().with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "home-visit-type"));
        AttendanceType sportsType = new AttendanceType();
        sportsType.setUuid("sports-type-uuid");
        sportsType.setConfig(new JsonObject().with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "phone-call-type"));

        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("prayer-type-uuid")).thenReturn(prayerType);
        when(attendanceTypeRepository.findByUuid("sports-type-uuid")).thenReturn(sportsType);
        when(encounterTypeRepository.findByUuid("home-visit-type")).thenReturn(homeVisit);
        when(encounterTypeRepository.findByUuid("phone-call-type")).thenReturn(phoneCall);
        when(encounterService.createEmptyEncounter(any(), any())).thenAnswer(inv -> {
            Encounter encounter = new Encounter();
            encounter.setIndividual(inv.getArgument(0));
            encounter.setEncounterType(inv.getArgument(1));
            return encounter;
        });

        SessionContract prayerSession = baseContract(SessionStatus.Held);
        prayerSession.setAttendanceTypeUUID("prayer-type-uuid");
        AttendanceRecordContract prayerAbsent = rosterEntry("subj-1", AttendanceStatus.Absent);
        prayerAbsent.setNeedsFollowUp(true);
        prayerSession.setRoster(List.of(prayerAbsent));
        SessionContract sportsSession = baseContract(SessionStatus.Held);
        sportsSession.setAttendanceTypeUUID("sports-type-uuid");
        AttendanceRecordContract sportsAbsent = rosterEntry("subj-1", AttendanceStatus.Absent);
        sportsAbsent.setNeedsFollowUp(true);
        sportsSession.setRoster(List.of(sportsAbsent));

        SessionSaveResult prayerResult = service.save(prayerSession);
        SessionSaveResult sportsResult = service.save(sportsSession);

        assertEquals(1, prayerResult.getAutoCreatedFollowUps().size());
        assertEquals("Home Visit", prayerResult.getAutoCreatedFollowUps().get(0).getEncounterTypeName());
        assertEquals(1, sportsResult.getAutoCreatedFollowUps().size());
        assertEquals("Phone Call", sportsResult.getAutoCreatedFollowUps().get(0).getEncounterTypeName());
        verify(encounterService, times(2)).save(any(Encounter.class));
    }

    @Test
    public void skipsFollowUpWhenEncounterTypeReferenceIsVoided() {
        Individual subject1 = subjectWithUuid("subj-1");
        EncounterType voidedType = new EncounterType();
        voidedType.setUuid("voided-type");
        voidedType.setName("Stale");
        voidedType.setVoided(true);
        attendanceType.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, "voided-type"));

        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(encounterTypeRepository.findByUuid("voided-type")).thenReturn(voidedType);

        SessionContract contract = baseContract(SessionStatus.Held);
        AttendanceRecordContract absent = rosterEntry("subj-1", AttendanceStatus.Absent);
        absent.setNeedsFollowUp(true);
        contract.setRoster(List.of(absent));

        SessionSaveResult result = service.save(contract);

        assertEquals(0, result.getAutoCreatedFollowUps().size());
        assertEquals(1, result.getWarnings().size());
        verify(encounterService, never()).save(any(Encounter.class));
    }

    @Test
    public void voidsStaleFollowUpWhenTriggerNoLongerHolds() {
        Individual subject1 = subjectWithUuid("subj-1");
        AttendanceRecord previousRecord = new AttendanceRecord();
        previousRecord.setUuid("record-uuid");
        previousRecord.setSubject(subject1);
        previousRecord.setStatus(AttendanceStatus.Absent);
        previousRecord.setNeedsFollowUp(true);
        previousRecord.setFollowUpEncounterUuid("encounter-uuid");

        Encounter encounter = new Encounter();
        encounter.setUuid("encounter-uuid");
        encounter.setObservations(new ObservationCollection());
        EncounterType encounterType = new EncounterType();
        encounterType.setName("Home Visit");
        encounter.setEncounterType(encounterType);

        Session existingSession = new Session();
        existingSession.setUuid("session-uuid");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(attendanceRecordRepository.findBySessionAndIsVoidedFalse(any())).thenReturn(List.of(previousRecord));
        when(encounterRepository.findAllByUuidIn(List.of("encounter-uuid"))).thenReturn(List.of(encounter));

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setUuid("session-uuid");
        AttendanceRecordContract updated = rosterEntry("subj-1", AttendanceStatus.Present);
        updated.setUuid("record-uuid");
        contract.setRoster(List.of(updated));

        SessionSaveResult result = service.update(existingSession, contract);

        assertEquals(1, result.getVoidedStaleFollowUps().size());
        assertTrue(encounter.isVoided());
    }

    @Test
    public void preservesFollowUpWhenStaleEncounterHasObservations() {
        Individual subject1 = subjectWithUuid("subj-1");
        AttendanceRecord previousRecord = new AttendanceRecord();
        previousRecord.setUuid("record-uuid");
        previousRecord.setSubject(subject1);
        previousRecord.setStatus(AttendanceStatus.Absent);
        previousRecord.setNeedsFollowUp(true);
        previousRecord.setFollowUpEncounterUuid("encounter-uuid");

        Encounter encounter = new Encounter();
        encounter.setUuid("encounter-uuid");
        ObservationCollection observations = new ObservationCollection();
        observations.put("any-concept-uuid", "value");
        encounter.setObservations(observations);
        EncounterType encounterType = new EncounterType();
        encounterType.setName("Home Visit");
        encounter.setEncounterType(encounterType);

        Session existingSession = new Session();
        existingSession.setUuid("session-uuid");
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(individualRepository.findByUuid("subj-1")).thenReturn(subject1);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);
        when(attendanceRecordRepository.findBySessionAndIsVoidedFalse(any())).thenReturn(List.of(previousRecord));
        when(encounterRepository.findAllByUuidIn(List.of("encounter-uuid"))).thenReturn(List.of(encounter));

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setUuid("session-uuid");
        AttendanceRecordContract updated = rosterEntry("subj-1", AttendanceStatus.Present);
        updated.setUuid("record-uuid");
        contract.setRoster(List.of(updated));

        SessionSaveResult result = service.update(existingSession, contract);

        assertEquals(0, result.getVoidedStaleFollowUps().size());
        assertEquals(1, result.getSkippedAlreadyFilledFollowUps().size());
        assertEquals(false, encounter.isVoided());
    }

    @Test
    public void deleteCascadesToRosterAndEmptyFollowUps() {
        Individual subject1 = subjectWithUuid("subj-1");
        AttendanceRecord record = new AttendanceRecord();
        record.setSubject(subject1);
        record.setFollowUpEncounterUuid("encounter-uuid");

        Encounter encounter = new Encounter();
        encounter.setUuid("encounter-uuid");
        encounter.setObservations(new ObservationCollection());
        EncounterType encounterType = new EncounterType();
        encounterType.setName("Home Visit");
        encounter.setEncounterType(encounterType);

        Session session = new Session();
        session.setUuid("session-uuid");
        when(attendanceRecordRepository.findBySessionAndIsVoidedFalse(session)).thenReturn(List.of(record));
        when(encounterRepository.findAllByUuidIn(List.of("encounter-uuid"))).thenReturn(List.of(encounter));

        SessionSaveResult result = service.delete(session);

        assertTrue(session.isVoided());
        assertTrue(record.isVoided());
        assertTrue(encounter.isVoided());
        assertEquals(1, result.getVoidedStaleFollowUps().size());
    }

    private Individual subjectWithUuid(String uuid) {
        Individual individual = new Individual();
        individual.setUuid(uuid);
        return individual;
    }

    private Concept conceptWithUuid(String uuid) {
        Concept concept = new Concept();
        concept.setUuid(uuid);
        return concept;
    }

    private SessionContract baseContract(SessionStatus status) {
        SessionContract contract = new SessionContract();
        contract.setGroupSubjectUUID("group-uuid");
        contract.setAttendanceTypeUUID("att-type-uuid");
        contract.setScheduledDate(LocalDate.of(2026, 1, 1));
        contract.setStatus(status);
        return contract;
    }

    private AttendanceRecordContract rosterEntry(String subjectUuid, AttendanceStatus status) {
        AttendanceRecordContract record = new AttendanceRecordContract();
        record.setSubjectUUID(subjectUuid);
        record.setStatus(status);
        return record;
    }
}
