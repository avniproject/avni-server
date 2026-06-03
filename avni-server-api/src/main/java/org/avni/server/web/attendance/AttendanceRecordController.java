package org.avni.server.web.attendance;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.attendance.AttendanceRecordService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.RestControllerResourceProcessor;
import org.avni.server.web.request.attendance.AttendanceRecordContract;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class AttendanceRecordController implements RestControllerResourceProcessor<AttendanceRecord> {

    private final AttendanceRecordService attendanceRecordService;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SessionRepository sessionRepository;
    private final IndividualRepository individualRepository;
    private final ConceptRepository conceptRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ScopeBasedSyncService<AttendanceRecord> scopeBasedSyncService;
    private final UserService userService;
    private final AccessControlService accessControlService;

    public AttendanceRecordController(AttendanceRecordService attendanceRecordService,
                                      AttendanceRecordRepository attendanceRecordRepository,
                                      SessionRepository sessionRepository,
                                      IndividualRepository individualRepository,
                                      ConceptRepository conceptRepository,
                                      SubjectTypeRepository subjectTypeRepository,
                                      ScopeBasedSyncService<AttendanceRecord> scopeBasedSyncService,
                                      UserService userService,
                                      AccessControlService accessControlService) {
        this.attendanceRecordService = attendanceRecordService;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.sessionRepository = sessionRepository;
        this.individualRepository = individualRepository;
        this.conceptRepository = conceptRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.userService = userService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = {"/attendanceRecord", "/attendanceRecord/search/lastModified"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public PagedModel<EntityModel<AttendanceRecord>> syncByLastModified(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty()) return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsByCatchment(
                attendanceRecordRepository, userService.getCurrentUser(),
                lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType,
                SyncEntityName.AttendanceRecord));
    }

    @GetMapping(value = "/attendanceRecord/search/lastModified/v2")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public SlicedResources<EntityModel<AttendanceRecord>> syncByLastModifiedAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty()) return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsByCatchmentAsSlice(
                attendanceRecordRepository, userService.getCurrentUser(),
                lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType,
                SyncEntityName.AttendanceRecord));
    }

    @GetMapping(value = "/web/attendanceRecord")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<AttendanceRecordContract> getAll(@RequestParam(value = "sessionUUID", required = false) String sessionUUID) {
        List<AttendanceRecord> records;
        if (sessionUUID != null) {
            Session session = sessionRepository.findByUuid(sessionUUID);
            if (session == null) {
                return List.of();
            }
            records = attendanceRecordRepository.findBySessionAndIsVoidedFalse(session);
        } else {
            records = attendanceRecordRepository.findAllByIsVoidedFalse();
        }
        return records.stream().map(AttendanceRecordContract::fromEntity).collect(Collectors.toList());
    }

    @GetMapping(value = "/web/attendanceRecord/{uuid}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<AttendanceRecordContract> getByUuid(@PathVariable String uuid) {
        AttendanceRecord record = attendanceRecordRepository.findByUuid(uuid);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AttendanceRecordContract.fromEntity(record));
    }

    @RequestMapping(value = "/attendanceRecord", method = RequestMethod.POST)
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void saveForSync(@RequestBody AttendanceRecordContract contract) {
        Session session = resolveSession(contract.getSessionUUID());
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, session.getGroupSubject());
        contract.setupUuidIfNeeded();
        AttendanceRecord existing = attendanceRecordRepository.findByUuid(contract.getUuid());
        AttendanceRecord record = existing != null ? existing : new AttendanceRecord();
        if (existing == null) {
            record.assignUUID(contract.getUuid());
        }
        applyContract(contract, record, session);
        attendanceRecordService.save(record);
    }

    @PostMapping(value = "/web/attendanceRecord")
    @ResponseBody
    @Transactional
    public ResponseEntity<AttendanceRecordContract> create(@RequestBody AttendanceRecordContract contract) {
        Session session = resolveSession(contract.getSessionUUID());
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, session.getGroupSubject());
        contract.setupUuidIfNeeded();
        AttendanceRecord record = new AttendanceRecord();
        record.assignUUID(contract.getUuid());
        applyContract(contract, record, session);
        AttendanceRecord saved = attendanceRecordService.save(record);
        return ResponseEntity.ok(AttendanceRecordContract.fromEntity(saved));
    }

    @PutMapping(value = "/web/attendanceRecord/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<AttendanceRecordContract> update(@PathVariable String uuid, @RequestBody AttendanceRecordContract contract) {
        AttendanceRecord record = attendanceRecordRepository.findByUuid(uuid);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, record.getSession().getGroupSubject());
        Session session = resolveSession(contract.getSessionUUID());
        if (!record.getSession().getId().equals(session.getId())) {
            accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, session.getGroupSubject());
        }
        applyContract(contract, record, session);
        AttendanceRecord saved = attendanceRecordService.save(record);
        return ResponseEntity.ok(AttendanceRecordContract.fromEntity(saved));
    }

    @DeleteMapping(value = "/web/attendanceRecord/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        AttendanceRecord record = attendanceRecordRepository.findByUuid(uuid);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, record.getSession().getGroupSubject());
        record.setVoided(true);
        attendanceRecordRepository.save(record);
        return ResponseEntity.ok().build();
    }

    private Session resolveSession(String uuid) {
        if (uuid == null) {
            throw new BadRequestError("sessionUUID is required");
        }
        Session session = sessionRepository.findByUuid(uuid);
        if (session == null) {
            throw new BadRequestError("Session not found: %s", uuid);
        }
        return session;
    }

    private void applyContract(AttendanceRecordContract contract, AttendanceRecord record, Session session) {
        Individual subject = individualRepository.findByUuid(contract.getSubjectUUID());
        if (subject == null) {
            throw new BadRequestError("Subject not found: %s", contract.getSubjectUUID());
        }
        Concept reasonConcept = contract.getReasonConceptUUID() == null ? null : conceptRepository.findByUuid(contract.getReasonConceptUUID());

        record.setSession(session);
        record.setSubject(subject);
        record.setStatus(contract.getStatus());
        record.setReasonConcept(reasonConcept);
        record.setFollowUpEncounterUuid(contract.getFollowUpEncounterUUID());
        record.setVoided(contract.isVoided());
    }
}
