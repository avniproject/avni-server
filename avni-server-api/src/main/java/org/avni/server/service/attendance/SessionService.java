package org.avni.server.service.attendance;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.attendance.SessionStatus;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ScopeAwareService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.attendance.AttendanceRecordContract;
import org.avni.server.web.request.attendance.SessionContract;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SessionService implements ScopeAwareService<Session> {

    public record SessionWithRoster(Session session, List<AttendanceRecord> roster) {}

    private final SessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final IndividualRepository individualRepository;
    private final AttendanceTypeRepository attendanceTypeRepository;
    private final ConceptRepository conceptRepository;
    private final SubjectTypeRepository subjectTypeRepository;

    public SessionService(SessionRepository sessionRepository,
                          AttendanceRecordRepository attendanceRecordRepository,
                          IndividualRepository individualRepository,
                          AttendanceTypeRepository attendanceTypeRepository,
                          ConceptRepository conceptRepository,
                          SubjectTypeRepository subjectTypeRepository) {
        this.sessionRepository = sessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.individualRepository = individualRepository;
        this.attendanceTypeRepository = attendanceTypeRepository;
        this.conceptRepository = conceptRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String subjectTypeUuid) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return false;
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchmentAndSubjectType(user, lastModifiedDateTime, subjectType.getId(), subjectType, SyncEntityName.Session);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<Session> repository() {
        return sessionRepository;
    }

    @Transactional
    public SessionWithRoster saveSessionWithRoster(SessionContract contract) {
        Session session = (contract.getUuid() != null) ? sessionRepository.findByUuid(contract.getUuid()) : null;
        if (session == null) {
            session = new Session();
            session.assignUUID(contract.getUuid());
        }
        Individual groupSubject = individualRepository.findByUuid(contract.getGroupSubjectUUID());
        if (groupSubject == null) {
            throw new BadRequestError("Group subject not found: %s", contract.getGroupSubjectUUID());
        }
        AttendanceType attendanceType = attendanceTypeRepository.findByUuid(contract.getAttendanceTypeUUID());
        if (attendanceType == null) {
            throw new BadRequestError("AttendanceType not found: %s", contract.getAttendanceTypeUUID());
        }
        Concept reasonConcept = contract.getReasonConceptUUID() == null ? null : conceptRepository.findByUuid(contract.getReasonConceptUUID());

        session.setGroupSubject(groupSubject);
        session.setScheduledDate(contract.getScheduledDate());
        session.setAttendanceType(attendanceType);
        session.setStatus(contract.getStatus());
        session.setReasonConcept(reasonConcept);
        session.setNotes(contract.getNotes());
        session.setMarkedByUser(UserContextHolder.getUserContext().getUser());
        session.setMarkedAt(DateTime.now());
        session.setVoided(contract.isVoided());
        Session saved = sessionRepository.save(session);

        List<AttendanceRecord> roster = Collections.emptyList();
        if (saved.getStatus() == SessionStatus.Held && contract.getRoster() != null && !contract.getRoster().isEmpty()) {
            roster = persistRoster(saved, contract.getRoster());
        }
        return new SessionWithRoster(saved, roster);
    }

    private List<AttendanceRecord> persistRoster(Session session, List<AttendanceRecordContract> rosterContracts) {
        Set<String> reasonConceptUuids = rosterContracts.stream()
                .map(AttendanceRecordContract::getReasonConceptUUID)
                .filter(uuid -> uuid != null)
                .collect(Collectors.toSet());
        Map<String, Concept> reasonConceptsByUuid = reasonConceptUuids.isEmpty() ? new HashMap<>() :
                conceptRepository.findAllByUuidIn(new ArrayList<>(reasonConceptUuids)).stream()
                        .collect(Collectors.toMap(Concept::getUuid, c -> c));

        List<AttendanceRecord> records = new ArrayList<>(rosterContracts.size());
        for (AttendanceRecordContract recordContract : rosterContracts) {
            AttendanceRecord record = (recordContract.getUuid() != null) ? attendanceRecordRepository.findByUuid(recordContract.getUuid()) : null;
            if (record == null) {
                record = new AttendanceRecord();
                record.assignUUID(recordContract.getUuid());
            }
            Individual subject = individualRepository.findByUuid(recordContract.getSubjectUUID());
            if (subject == null) {
                throw new BadRequestError("Subject not found in roster: %s", recordContract.getSubjectUUID());
            }
            Concept reasonConcept = recordContract.getReasonConceptUUID() == null ? null : reasonConceptsByUuid.get(recordContract.getReasonConceptUUID());
            record.setSession(session);
            record.setSubject(subject);
            record.setStatus(recordContract.getStatus());
            record.setReasonConcept(reasonConcept);
            record.setFollowUpEncounterUuid(recordContract.getFollowUpEncounterUuid());
            record.setVoided(recordContract.isVoided());
            records.add(record);
        }
        List<AttendanceRecord> saved = new ArrayList<>(records.size());
        attendanceRecordRepository.saveAll(records).forEach(saved::add);
        return saved;
    }
}
