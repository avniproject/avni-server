package org.avni.server.service.attendance;

import org.avni.server.common.EntityHelper;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Concept;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.service.NonScopeAwareService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.attendance.AttendanceTypeContract;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AttendanceTypeService implements NonScopeAwareService {
    private final AttendanceTypeRepository attendanceTypeRepository;
    private final ConceptRepository conceptRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final SubjectTypeRepository subjectTypeRepository;

    public AttendanceTypeService(AttendanceTypeRepository attendanceTypeRepository,
                                 ConceptRepository conceptRepository,
                                 EncounterTypeRepository encounterTypeRepository,
                                 SubjectTypeRepository subjectTypeRepository) {
        this.attendanceTypeRepository = attendanceTypeRepository;
        this.conceptRepository = conceptRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Transactional
    public AttendanceType save(AttendanceType attendanceType) {
        return save(attendanceType, false);
    }

    @Transactional
    public AttendanceType save(AttendanceType attendanceType, boolean fromBundle) {
        if (!attendanceType.isVoided()) {
            validateConfig(attendanceType.getConfig(), fromBundle);
        } else if (!fromBundle) {
            // Interactive-only guard. A bundle mirrors source state: a voided row may be
            // processed before any live row for its subject type exists in the destination,
            // and the source already holds at least one live row per attendance-enabled
            // subject type, which the same file carries.
            validateNotLast(attendanceType);
        }
        attendanceType.assignUUIDIfRequired();
        return attendanceTypeRepository.save(attendanceType);
    }

    public void validateConfig(JsonObject config) {
        validateConfig(config, false);
    }

    private void validateConfig(JsonObject config, boolean allowVoidedReferences) {
        if (config == null || config.isEmpty()) {
            return;
        }
        for (String key : config.keySet()) {
            if (!AttendanceTypeConfigKey.ALL.contains(key)) {
                throw new BadRequestError("AttendanceType.config contains unknown key '%s'. Allowed: %s", key, AttendanceTypeConfigKey.ALL);
            }
        }
        for (String key : AttendanceTypeConfigKey.CONCEPT_UUID_KEYS) {
            String uuid = AttendanceTypeConfigKey.stringValue(config, key);
            if (uuid != null) {
                Concept concept = conceptRepository.findByUuid(uuid);
                if (concept == null || (concept.isVoided() && !allowVoidedReferences)) {
                    throw new BadRequestError("AttendanceType.config.%s references unknown or voided concept: %s", key, uuid);
                }
            }
        }
        for (String key : AttendanceTypeConfigKey.ENCOUNTER_TYPE_UUID_KEYS) {
            String uuid = AttendanceTypeConfigKey.stringValue(config, key);
            if (uuid != null) {
                EncounterType encounterType = encounterTypeRepository.findByUuid(uuid);
                if (encounterType == null || (encounterType.isVoided() && !allowVoidedReferences)) {
                    throw new BadRequestError("AttendanceType.config.%s references unknown or voided encounter type: %s", key, uuid);
                }
            }
        }
    }

    private void validateNotLast(AttendanceType toVoid) {
        SubjectType subjectType = toVoid.getSubjectType();
        if (subjectType == null || !subjectType.isAttendanceEnabled()) {
            return;
        }
        List<AttendanceType> nonVoided = attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(subjectType);
        long remaining = nonVoided.stream()
                .filter(at -> !at.getUuid().equals(toVoid.getUuid()))
                .count();
        if (remaining == 0) {
            throw new BadRequestError("Cannot void the last AttendanceType while attendance is enabled on subject type %s", subjectType.getName());
        }
    }

    public List<DanglingRefWarning> surfaceDanglingReferences(AttendanceType attendanceType) {
        List<DanglingRefWarning> warnings = new ArrayList<>();
        JsonObject config = attendanceType.getConfig();
        if (config == null || config.isEmpty()) {
            return warnings;
        }
        for (String key : AttendanceTypeConfigKey.CONCEPT_UUID_KEYS) {
            String uuid = AttendanceTypeConfigKey.stringValue(config, key);
            if (uuid == null) continue;
            Concept concept = conceptRepository.findByUuid(uuid);
            if (concept == null) {
                warnings.add(new DanglingRefWarning(attendanceType.getUuid(), attendanceType.getName(), key, uuid, "Concept not found"));
            } else if (concept.isVoided()) {
                warnings.add(new DanglingRefWarning(attendanceType.getUuid(), attendanceType.getName(), key, uuid, "Concept is voided"));
            }
        }
        for (String key : AttendanceTypeConfigKey.ENCOUNTER_TYPE_UUID_KEYS) {
            String uuid = AttendanceTypeConfigKey.stringValue(config, key);
            if (uuid == null) continue;
            EncounterType encounterType = encounterTypeRepository.findByUuid(uuid);
            if (encounterType == null) {
                warnings.add(new DanglingRefWarning(attendanceType.getUuid(), attendanceType.getName(), key, uuid, "EncounterType not found"));
            } else if (encounterType.isVoided()) {
                warnings.add(new DanglingRefWarning(attendanceType.getUuid(), attendanceType.getName(), key, uuid, "EncounterType is voided"));
            }
        }
        return warnings;
    }

    @Transactional
    public void saveFromBundle(AttendanceTypeContract[] contracts) {
        for (AttendanceTypeContract contract : contracts) {
            AttendanceType attendanceType = EntityHelper.newOrExistingEntity(attendanceTypeRepository, contract.getUuid(), new AttendanceType());
            SubjectType subjectType = subjectTypeRepository.findByUuid(contract.getSubjectTypeUUID());
            if (subjectType == null) {
                throw new BadRequestError("AttendanceType bundle row references unknown SubjectType uuid: %s", contract.getSubjectTypeUUID());
            }
            if (attendanceType.getId() == null && !contract.isVoided()) {
                // Destination may already have a non-voided AttendanceType with the same
                // (subject_type, lower(name)) — typically the seedDefaultAttendanceTypeIfEnabling row
                // with config={}, whose UUID won't match anything in the source bundle. Void that
                // seed row so the partial unique index (where is_voided=false) is freed before we
                // insert the bundle row. saveAndFlush forces the UPDATE ahead of Hibernate's INSERT
                // in the same transaction. If the existing row has admin-customised config, refuse
                // rather than overwrite silently.
                AttendanceType nameConflict = attendanceTypeRepository.findBySubjectTypeAndNameIgnoreCaseAndIsVoidedFalse(subjectType, contract.getName());
                if (nameConflict != null && !nameConflict.getUuid().equals(contract.getUuid())) {
                    JsonObject existingConfig = nameConflict.getConfig();
                    if (existingConfig == null || existingConfig.isEmpty()) {
                        nameConflict.setVoided(true);
                        attendanceTypeRepository.saveAndFlush(nameConflict);
                    } else {
                        throw new BadRequestError("AttendanceType '%s' for subject type '%s' already exists in destination with custom config; resolve manually before importing.", contract.getName(), subjectType.getUuid());
                    }
                }
            }
            attendanceType.setSubjectType(subjectType);
            attendanceType.setName(contract.getName());
            attendanceType.setSortOrder(contract.getSortOrder());
            JsonObject config = contract.getConfig() == null ? new JsonObject() : new JsonObject(contract.getConfig());
            attendanceType.setConfig(config);
            attendanceType.setVoided(contract.isVoided());
            // fromBundle=true: tolerate references to voided concepts/encounter types (the
            // source org holds these as a warned state — see surfaceDanglingReferences) and
            // skip the interactive last-row voiding guard.
            save(attendanceType, true);
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return attendanceTypeRepository.existsByLastModifiedDateTimeGreaterThan(CHSEntity.toDate(lastModifiedDateTime));
    }
}
