package org.avni.server.service.attendance;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.AttendanceStatus;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.attendance.SessionStatus;
import org.avni.server.framework.security.UserContextHolder;
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

    private SessionService service;

    private Individual groupSubject;
    private AttendanceType attendanceType;

    @Before
    public void setUp() {
        initMocks(this);
        service = new SessionService(sessionRepository, attendanceRecordRepository, individualRepository, attendanceTypeRepository, conceptRepository);

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

        SessionService.SessionWithRoster result = service.saveSessionWithRoster(contract);

        verify(sessionRepository, times(1)).save(any(Session.class));
        ArgumentCaptor<List<AttendanceRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(attendanceRecordRepository, times(1)).saveAll(captor.capture());
        List<AttendanceRecord> persisted = captor.getValue();
        assertEquals(2, persisted.size());
        assertSame(subject1, persisted.get(0).getSubject());
        assertEquals(AttendanceStatus.Present, persisted.get(0).getStatus());
        assertSame(subject2, persisted.get(1).getSubject());
        assertEquals(AttendanceStatus.Absent, persisted.get(1).getStatus());
        assertEquals(SessionStatus.Held, result.session().getStatus());
        assertEquals(2, result.roster().size());
    }

    @Test
    public void didntHappenSessionSkipsRosterPersist() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);

        SessionContract contract = baseContract(SessionStatus.DidntHappen);
        contract.setRoster(List.of(rosterEntry("subj-1", AttendanceStatus.Present)));

        SessionService.SessionWithRoster result = service.saveSessionWithRoster(contract);

        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
        assertTrue(result.roster().isEmpty());
    }

    @Test
    public void missingGroupSubjectThrowsBeforeAnyPersist() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(null);

        try {
            service.saveSessionWithRoster(baseContract(SessionStatus.Held));
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
            service.saveSessionWithRoster(baseContract(SessionStatus.Held));
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
            service.saveSessionWithRoster(contract);
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
        Concept reason = new Concept();
        reason.setUuid("reason-uuid");
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

        service.saveSessionWithRoster(contract);

        verify(conceptRepository, times(1)).findAllByUuidIn(anyList());
        verify(conceptRepository, never()).findByUuid(any());
    }

    @Test
    public void heldSessionWithEmptyRosterPersistsSessionOnly() {
        when(individualRepository.findByUuid("group-uuid")).thenReturn(groupSubject);
        when(attendanceTypeRepository.findByUuid("att-type-uuid")).thenReturn(attendanceType);

        SessionContract contract = baseContract(SessionStatus.Held);
        contract.setRoster(Collections.emptyList());

        SessionService.SessionWithRoster result = service.saveSessionWithRoster(contract);

        verify(sessionRepository, times(1)).save(any(Session.class));
        verify(attendanceRecordRepository, never()).saveAll(anyList());
        assertTrue(result.roster().isEmpty());
    }

    private Individual subjectWithUuid(String uuid) {
        Individual individual = new Individual();
        individual.setUuid(uuid);
        return individual;
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
