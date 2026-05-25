package org.avni.server.service.attendance;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.dao.attendance.SessionRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.common.EntityHelper;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.AttendanceStatus;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.domain.attendance.Session;
import org.avni.server.domain.attendance.SessionStatus;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.DayType;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.EncounterService;
import org.avni.server.service.ScopeAwareService;
import org.avni.server.service.calendar.CalendarsResolver;
import org.avni.server.service.calendar.DayTypeResolver;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.attendance.AttendanceRecordContract;
import org.avni.server.web.request.attendance.SessionContract;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionService implements ScopeAwareService<Session> {

    private static final int FOLLOW_UP_WINDOW_DAYS = 2;

    private final SessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final IndividualRepository individualRepository;
    private final AttendanceTypeRepository attendanceTypeRepository;
    private final ConceptRepository conceptRepository;
    private final CalendarRepository calendarRepository;
    private final CalendarDateMarkerRepository calendarDateMarkerRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterService encounterService;
    private final SubjectTypeRepository subjectTypeRepository;

    public SessionService(SessionRepository sessionRepository,
                          AttendanceRecordRepository attendanceRecordRepository,
                          IndividualRepository individualRepository,
                          AttendanceTypeRepository attendanceTypeRepository,
                          ConceptRepository conceptRepository,
                          CalendarRepository calendarRepository,
                          CalendarDateMarkerRepository calendarDateMarkerRepository,
                          EncounterTypeRepository encounterTypeRepository,
                          EncounterRepository encounterRepository,
                          EncounterService encounterService,
                          SubjectTypeRepository subjectTypeRepository) {
        this.sessionRepository = sessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.individualRepository = individualRepository;
        this.attendanceTypeRepository = attendanceTypeRepository;
        this.conceptRepository = conceptRepository;
        this.calendarRepository = calendarRepository;
        this.calendarDateMarkerRepository = calendarDateMarkerRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.encounterRepository = encounterRepository;
        this.encounterService = encounterService;
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
    public SessionSaveResult save(SessionContract contract) {
        return saveInternal(contract, null);
    }

    @Transactional
    public SessionSaveResult update(Session existing, SessionContract contract) {
        return saveInternal(contract, existing);
    }

    @Transactional
    public SessionSaveResult delete(Session session) {
        List<AttendanceRecord> existingRecords = attendanceRecordRepository.findBySessionAndIsVoidedFalse(session);
        List<FollowUpDescriptor> voidedFollowUps = new ArrayList<>();
        List<SkippedFollowUp> skippedFollowUps = new ArrayList<>();
        Map<String, Encounter> followUpsByUuid = batchLoadFollowUps(existingRecords);
        for (AttendanceRecord record : existingRecords) {
            handleFollowUpVoid(record, followUpsByUuid, voidedFollowUps, skippedFollowUps);
            record.setVoided(true);
        }
        attendanceRecordRepository.saveAll(existingRecords);
        session.setVoided(true);
        Session saved = sessionRepository.save(session);
        return new SessionSaveResult(saved, existingRecords, Collections.emptyList(), voidedFollowUps, skippedFollowUps, Collections.emptyList());
    }

    private Map<String, Encounter> batchLoadFollowUps(Iterable<AttendanceRecord> records) {
        List<String> uuids = new ArrayList<>();
        records.forEach(r -> {
            if (r.getFollowUpEncounterUuid() != null) uuids.add(r.getFollowUpEncounterUuid());
        });
        if (uuids.isEmpty()) return Collections.emptyMap();
        return encounterRepository.findAllByUuidIn(uuids).stream()
                .collect(Collectors.toMap(Encounter::getUuid, e -> e));
    }

    private SessionSaveResult saveInternal(SessionContract contract, Session preloaded) {
        rejectFutureDate(contract.getScheduledDate());

        Session session = preloaded != null ? preloaded
                : (contract.getUuid() != null ? sessionRepository.findByUuid(contract.getUuid()) : null);
        boolean creating = (session == null);
        if (creating) {
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
        Concept sessionReasonConcept = contract.getReasonConceptUUID() == null ? null
                : conceptRepository.findByUuid(contract.getReasonConceptUUID());

        DayType dayType = resolveDayType(groupSubject, contract.getScheduledDate());
        if (!contract.isVoided()) {
            validateSessionReason(contract.getStatus(), dayType, contract.getReasonConceptUUID());
        }

        Map<String, AttendanceRecord> existingRecordsBySubjectUuid = creating
                ? new HashMap<>()
                : attendanceRecordRepository.findBySessionAndIsVoidedFalse(session).stream()
                .collect(Collectors.toMap(AttendanceRecord::getSubjectUUID, r -> r, (a, b) -> a));

        session.setGroupSubject(groupSubject);
        session.setScheduledDate(contract.getScheduledDate());
        session.setAttendanceType(attendanceType);
        session.setStatus(contract.getStatus());
        session.setReasonConcept(sessionReasonConcept);
        session.setNotes(contract.getNotes());
        session.setMarkedByUser(UserContextHolder.getUserContext().getUser());
        session.setMarkedAt(DateTime.now());
        session.setVoided(contract.isVoided());
        Session saved = sessionRepository.save(session);

        List<AttendanceRecord> persistedRoster = Collections.emptyList();
        List<FollowUpDescriptor> autoCreatedFollowUps = new ArrayList<>();
        List<FollowUpDescriptor> voidedStaleFollowUps = new ArrayList<>();
        List<SkippedFollowUp> skippedFollowUps = new ArrayList<>();
        List<DanglingRefWarning> warnings = new ArrayList<>();

        if (saved.getStatus() == SessionStatus.Held && contract.getRoster() != null && !contract.getRoster().isEmpty()) {
            EncounterType followUpType = resolveFollowUpEncounterType(attendanceType, warnings);
            persistedRoster = persistRoster(saved, contract.getRoster(), existingRecordsBySubjectUuid,
                    followUpType, autoCreatedFollowUps);
        }

        if (!creating) {
            voidStaleFollowUps(existingRecordsBySubjectUuid, persistedRoster, voidedStaleFollowUps, skippedFollowUps);
        }

        return new SessionSaveResult(saved, persistedRoster, autoCreatedFollowUps, voidedStaleFollowUps, skippedFollowUps, warnings);
    }

    private void rejectFutureDate(LocalDate scheduledDate) {
        if (scheduledDate == null) {
            throw new BadRequestError("scheduledDate is required");
        }
        if (scheduledDate.isAfter(LocalDate.now())) {
            throw new FutureScheduledDateNotAllowedException(scheduledDate);
        }
    }

    private DayType resolveDayType(Individual groupSubject, LocalDate date) {
        Calendar calendar = CalendarsResolver.forSubject(groupSubject, calendarRepository);
        return DayTypeResolver.resolve(calendar, date, calendarDateMarkerRepository);
    }

    private void validateSessionReason(SessionStatus status, DayType dayType, String reasonUuid) {
        if (status == SessionStatus.DidntHappen && reasonUuid == null) {
            throw new ReasonRequiredException(dayType, ReasonRequiredException.RequiredFor.DidntHappen);
        }
        if (status == SessionStatus.Held
                && (dayType == DayType.public_holiday || dayType == DayType.weekly_off)
                && reasonUuid == null) {
            throw new ReasonRequiredException(dayType, ReasonRequiredException.RequiredFor.MarkAnywayHeld);
        }
    }

    private EncounterType resolveFollowUpEncounterType(AttendanceType attendanceType, List<DanglingRefWarning> warnings) {
        JsonObject config = attendanceType.getConfig();
        if (config == null) {
            return null;
        }
        String followUpUuid = AttendanceTypeConfigKey.stringValue(config, AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE);
        if (followUpUuid == null) {
            return null;
        }
        EncounterType encounterType = encounterTypeRepository.findByUuid(followUpUuid);
        if (encounterType == null || encounterType.isVoided()) {
            warnings.add(new DanglingRefWarning(attendanceType.getUuid(), attendanceType.getName(),
                    AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE, followUpUuid,
                    encounterType == null ? "EncounterType not found" : "EncounterType is voided"));
            return null;
        }
        return encounterType;
    }

    private List<AttendanceRecord> persistRoster(Session session,
                                                 List<AttendanceRecordContract> rosterContracts,
                                                 Map<String, AttendanceRecord> existingBySubject,
                                                 EncounterType followUpType,
                                                 List<FollowUpDescriptor> autoCreatedFollowUps) {
        Map<String, Concept> reasonConceptsByUuid = preloadReasonConcepts(rosterContracts);
        Map<String, Encounter> preExistingFollowUps = batchLoadExistingFollowUps(rosterContracts, existingBySubject);

        List<AttendanceRecord> records = new ArrayList<>(rosterContracts.size());
        List<Encounter> followUpEncountersToSave = new ArrayList<>();

        for (AttendanceRecordContract recordContract : rosterContracts) {
            Individual subject = individualRepository.findByUuid(recordContract.getSubjectUUID());
            if (subject == null) {
                throw new BadRequestError("Subject not found in roster: %s", recordContract.getSubjectUUID());
            }
            AttendanceRecord record = resolveAttendanceRecord(recordContract, existingBySubject);
            Concept reasonConcept = recordContract.getReasonConceptUUID() == null ? null
                    : reasonConceptsByUuid.get(recordContract.getReasonConceptUUID());
            record.setSession(session);
            record.setSubject(subject);
            record.setStatus(recordContract.getStatus());
            record.setReasonConcept(reasonConcept);
            record.setVoided(recordContract.isVoided());

            String preservedFollowUpUuid = record.getFollowUpEncounterUuid();
            if (preservedFollowUpUuid == null) {
                preservedFollowUpUuid = recordContract.getFollowUpEncounterUUID();
            }
            record.setFollowUpEncounterUuid(preservedFollowUpUuid);

            maybeCreateFollowUp(record, subject, followUpType, preExistingFollowUps, followUpEncountersToSave, autoCreatedFollowUps);
            records.add(record);
        }

        List<AttendanceRecord> savedRecords = new ArrayList<>(records.size());
        attendanceRecordRepository.saveAll(records).forEach(savedRecords::add);
        for (Encounter followUp : followUpEncountersToSave) {
            encounterService.save(followUp);
        }
        return savedRecords;
    }

    // Pre-load follow-up encounters keyed by uuid so maybeCreateFollowUp never re-inserts an existing one.
    // Both sources matter: the uuid the client echoed in the contract (Android pre-create) AND the uuid
    // carried over from a DB record the web client did not echo. Missing the latter causes a duplicate
    // insert that violates the unique constraint.
    private Map<String, Encounter> batchLoadExistingFollowUps(List<AttendanceRecordContract> contracts,
                                                              Map<String, AttendanceRecord> existingBySubject) {
        Set<String> uuids = contracts.stream()
                .map(AttendanceRecordContract::getFollowUpEncounterUUID)
                .filter(u -> u != null)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        existingBySubject.values().stream()
                .map(AttendanceRecord::getFollowUpEncounterUuid)
                .filter(u -> u != null)
                .forEach(uuids::add);
        if (uuids.isEmpty()) return Collections.emptyMap();
        return encounterRepository.findAllByUuidIn(new ArrayList<>(uuids)).stream()
                .collect(Collectors.toMap(Encounter::getUuid, e -> e));
    }

    private Map<String, Concept> preloadReasonConcepts(List<AttendanceRecordContract> rosterContracts) {
        Set<String> reasonConceptUuids = rosterContracts.stream()
                .map(AttendanceRecordContract::getReasonConceptUUID)
                .filter(uuid -> uuid != null)
                .collect(Collectors.toSet());
        if (reasonConceptUuids.isEmpty()) {
            return new HashMap<>();
        }
        return conceptRepository.findAllByUuidIn(new ArrayList<>(reasonConceptUuids)).stream()
                .collect(Collectors.toMap(Concept::getUuid, c -> c));
    }

    private AttendanceRecord resolveAttendanceRecord(AttendanceRecordContract recordContract,
                                                     Map<String, AttendanceRecord> existingBySubject) {
        AttendanceRecord existing = existingBySubject.remove(recordContract.getSubjectUUID());
        if (existing != null) {
            return existing;
        }
        if (recordContract.getUuid() != null) {
            AttendanceRecord byUuid = attendanceRecordRepository.findByUuid(recordContract.getUuid());
            if (byUuid != null) {
                return byUuid;
            }
        }
        AttendanceRecord record = new AttendanceRecord();
        record.assignUUID(recordContract.getUuid());
        return record;
    }

    private void maybeCreateFollowUp(AttendanceRecord record,
                                     Individual subject,
                                     EncounterType followUpType,
                                     Map<String, Encounter> preExistingFollowUps,
                                     List<Encounter> followUpsToSave,
                                     List<FollowUpDescriptor> autoCreatedFollowUps) {
        if (!triggersFollowUp(record) || followUpType == null) {
            return;
        }
        String existingUuid = record.getFollowUpEncounterUuid();
        if (existingUuid != null && preExistingFollowUps.containsKey(existingUuid)) {
            return;
        }
        String useUuid = existingUuid != null ? existingUuid : UUID.randomUUID().toString();
        Encounter encounter = encounterService.createEmptyEncounter(subject, followUpType);
        encounter.setUuid(useUuid);
        DateTime startOfToday = DateTime.now().withTimeAtStartOfDay();
        encounter.setEarliestVisitDateTime(startOfToday);
        encounter.setMaxVisitDateTime(startOfToday.plusDays(FOLLOW_UP_WINDOW_DAYS));
        followUpsToSave.add(encounter);
        record.setFollowUpEncounterUuid(useUuid);
        autoCreatedFollowUps.add(new FollowUpDescriptor(
                subject.getUuid(),
                subjectDisplayName(subject),
                useUuid,
                followUpType.getName(),
                startOfToday,
                startOfToday.plusDays(FOLLOW_UP_WINDOW_DAYS)
        ));
    }

    private boolean triggersFollowUp(AttendanceRecord record) {
        return !record.isVoided()
                && record.getStatus() == AttendanceStatus.Absent
                && record.getReasonConcept() == null;
    }

    // previouslyUnmatched holds prior records dropped from the new roster (resolveAttendanceRecord removes
    // matched ones); currentRoster holds re-marked records that no longer trigger a follow-up. Both sets
    // are followed by their attendance_record.follow_up_encounter_uuid FK, not by current config.
    private void voidStaleFollowUps(Map<String, AttendanceRecord> previouslyUnmatched,
                                    List<AttendanceRecord> currentRoster,
                                    List<FollowUpDescriptor> voidedStaleFollowUps,
                                    List<SkippedFollowUp> skippedFollowUps) {
        List<AttendanceRecord> staleCandidates = new ArrayList<>(previouslyUnmatched.values());
        for (AttendanceRecord record : currentRoster) {
            if (record.getFollowUpEncounterUuid() != null && !triggersFollowUp(record)) {
                staleCandidates.add(record);
            }
        }
        Map<String, Encounter> followUpsByUuid = batchLoadFollowUps(staleCandidates);
        for (AttendanceRecord record : staleCandidates) {
            handleFollowUpVoid(record, followUpsByUuid, voidedStaleFollowUps, skippedFollowUps);
        }
    }

    private void handleFollowUpVoid(AttendanceRecord record,
                                    Map<String, Encounter> followUpsByUuid,
                                    List<FollowUpDescriptor> voidedFollowUps,
                                    List<SkippedFollowUp> skippedFollowUps) {
        String followUpUuid = record.getFollowUpEncounterUuid();
        if (followUpUuid == null) {
            return;
        }
        Encounter encounter = followUpsByUuid.get(followUpUuid);
        if (encounter == null || encounter.isVoided()) {
            return;
        }
        Individual subject = record.getSubject();
        String studentUuid = subject == null ? null : subject.getUuid();
        String studentName = subject == null ? null : subjectDisplayName(subject);
        if (hasObservations(encounter)) {
            skippedFollowUps.add(new SkippedFollowUp(studentUuid, studentName, encounter.getUuid(),
                    "Encounter already has Observations"));
            return;
        }
        encounter.setVoided(true);
        encounterService.save(encounter);
        EncounterType type = encounter.getEncounterType();
        voidedFollowUps.add(new FollowUpDescriptor(studentUuid, studentName, encounter.getUuid(),
                type == null ? null : type.getName(),
                encounter.getEarliestVisitDateTime(), encounter.getMaxVisitDateTime()));
    }

    private boolean hasObservations(Encounter encounter) {
        ObservationCollection obs = encounter.getObservations();
        return obs != null && !obs.isEmpty();
    }

    private String subjectDisplayName(Individual subject) {
        String first = subject.getFirstName();
        String last = subject.getLastName();
        if (first == null && last == null) return null;
        if (last == null) return first;
        if (first == null) return last;
        return first + " " + last;
    }
}
