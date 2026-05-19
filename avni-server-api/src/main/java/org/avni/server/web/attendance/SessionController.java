package org.avni.server.web.attendance;

import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.Session;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.attendance.SessionService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.attendance.SessionContract;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SessionController {

    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final IndividualRepository individualRepository;
    private final AccessControlService accessControlService;

    public SessionController(SessionService sessionService,
                             SessionRepository sessionRepository,
                             AttendanceRecordRepository attendanceRecordRepository,
                             IndividualRepository individualRepository,
                             AccessControlService accessControlService) {
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.individualRepository = individualRepository;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/session")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<SessionContract> getAll(@RequestParam(value = "groupSubjectUUID", required = false) String groupSubjectUUID,
                                        @RequestParam(value = "scheduledDateFrom", required = false) LocalDate scheduledDateFrom,
                                        @RequestParam(value = "scheduledDateTo", required = false) LocalDate scheduledDateTo) {
        List<Session> sessions;
        if (groupSubjectUUID != null && scheduledDateFrom != null && scheduledDateTo != null) {
            Individual groupSubject = individualRepository.findByUuid(groupSubjectUUID);
            if (groupSubject == null) {
                return List.of();
            }
            sessions = sessionRepository.findByGroupSubjectAndScheduledDateBetweenAndIsVoidedFalseOrderByScheduledDateAsc(
                    groupSubject, scheduledDateFrom, scheduledDateTo);
        } else {
            sessions = sessionRepository.findAllByIsVoidedFalse();
        }
        return buildContracts(sessions);
    }

    private List<SessionContract> buildContracts(List<Session> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<AttendanceRecord>> recordsBySessionId = attendanceRecordRepository.findBySessionInAndIsVoidedFalse(sessions).stream()
                .collect(Collectors.groupingBy(r -> r.getSession().getId()));
        return sessions.stream()
                .map(s -> SessionContract.fromEntity(s, recordsBySessionId.getOrDefault(s.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/session/{uuid}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<SessionContract> getByUuid(@PathVariable String uuid) {
        Session session = sessionRepository.findByUuid(uuid);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        List<AttendanceRecord> records = attendanceRecordRepository.findBySessionAndIsVoidedFalse(session);
        return ResponseEntity.ok(SessionContract.fromEntity(session, records));
    }

    @PostMapping(value = "/web/session")
    @ResponseBody
    @Transactional
    public ResponseEntity<SessionContract> create(@RequestBody SessionContract contract) {
        Individual groupSubject = resolveGroupSubject(contract.getGroupSubjectUUID());
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, groupSubject);
        contract.setupUuidIfNeeded();
        SessionService.SessionWithRoster result = sessionService.saveSessionWithRoster(contract);
        return ResponseEntity.ok(SessionContract.fromEntity(result.session(), result.roster()));
    }

    @PutMapping(value = "/web/session/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<SessionContract> update(@PathVariable String uuid, @RequestBody SessionContract contract) {
        Session existing = sessionRepository.findByUuid(uuid);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (contract.getUuid() != null && !uuid.equals(contract.getUuid())) {
            throw new BadRequestError("Path uuid %s does not match body uuid %s", uuid, contract.getUuid());
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, existing.getGroupSubject());
        Individual newGroupSubject = resolveGroupSubject(contract.getGroupSubjectUUID());
        if (!existing.getGroupSubject().getId().equals(newGroupSubject.getId())) {
            accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, newGroupSubject);
        }
        contract.setUuid(uuid);
        SessionService.SessionWithRoster result = sessionService.saveSessionWithRoster(contract);
        return ResponseEntity.ok(SessionContract.fromEntity(result.session(), result.roster()));
    }

    @DeleteMapping(value = "/web/session/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        Session session = sessionRepository.findByUuid(uuid);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, session.getGroupSubject());
        session.setVoided(true);
        sessionRepository.save(session);
        return ResponseEntity.ok().build();
    }

    private Individual resolveGroupSubject(String uuid) {
        if (uuid == null) {
            throw new BadRequestError("groupSubjectUUID is required");
        }
        Individual groupSubject = individualRepository.findByUuid(uuid);
        if (groupSubject == null) {
            throw new BadRequestError("Group subject not found: %s", uuid);
        }
        return groupSubject;
    }
}
