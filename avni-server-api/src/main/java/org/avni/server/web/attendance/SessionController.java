package org.avni.server.web.attendance;

import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.attendance.SessionSaveResult;
import org.avni.server.service.attendance.SessionService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.RestControllerResourceProcessor;
import org.avni.server.web.request.attendance.SessionContract;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.avni.server.web.response.attendance.SessionSaveResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class SessionController implements RestControllerResourceProcessor<Session> {

    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final IndividualRepository individualRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ScopeBasedSyncService<Session> scopeBasedSyncService;
    private final UserService userService;
    private final AccessControlService accessControlService;

    public SessionController(SessionService sessionService,
                             SessionRepository sessionRepository,
                             AttendanceRecordRepository attendanceRecordRepository,
                             IndividualRepository individualRepository,
                             SubjectTypeRepository subjectTypeRepository,
                             ScopeBasedSyncService<Session> scopeBasedSyncService,
                             UserService userService,
                             AccessControlService accessControlService) {
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.individualRepository = individualRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.userService = userService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = {"/session", "/session/search/lastModified"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public PagedModel<EntityModel<Session>> syncByLastModified(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsByCatchment(
                sessionRepository, userService.getCurrentUser(),
                lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType,
                SyncEntityName.Session));
    }

    @GetMapping(value = "/session/search/lastModified/v2")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public SlicedResources<EntityModel<Session>> syncByLastModifiedAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsByCatchmentAsSlice(
                sessionRepository, userService.getCurrentUser(),
                lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType,
                SyncEntityName.Session));
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

    @RequestMapping(value = "/session", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void saveForSync(@RequestBody SessionContract contract) {
        Individual groupSubject = resolveGroupSubject(contract.getGroupSubjectUUID());
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, groupSubject);
        contract.setupUuidIfNeeded();
        Session existing = sessionRepository.findByUuid(contract.getUuid());
        if (existing == null) {
            sessionService.save(contract);
        } else {
            sessionService.update(existing, contract);
        }
    }

    @PostMapping(value = "/web/session")
    @ResponseBody
    @Transactional
    public ResponseEntity<SessionSaveResponse> create(@RequestBody SessionContract contract) {
        Individual groupSubject = resolveGroupSubject(contract.getGroupSubjectUUID());
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, groupSubject);
        contract.setupUuidIfNeeded();
        SessionSaveResult result = sessionService.save(contract);
        return ResponseEntity.ok(SessionSaveResponse.fromResult(result));
    }

    @PutMapping(value = "/web/session/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<SessionSaveResponse> update(@PathVariable String uuid, @RequestBody SessionContract contract) {
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
        SessionSaveResult result = sessionService.update(existing, contract);
        return ResponseEntity.ok(SessionSaveResponse.fromResult(result));
    }

    @DeleteMapping(value = "/web/session/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<SessionSaveResponse> delete(@PathVariable String uuid) {
        Session session = sessionRepository.findByUuid(uuid);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, session.getGroupSubject());
        SessionSaveResult result = sessionService.delete(session);
        return ResponseEntity.ok(SessionSaveResponse.fromResult(result));
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
