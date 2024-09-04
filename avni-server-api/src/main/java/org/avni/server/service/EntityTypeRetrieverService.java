package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EntityTypeRetrieverService {
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;

    @Autowired
    public EntityTypeRetrieverService(SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
    }

    public CHSEntity getEntityType(String entityType, Long entityTypeId) {
        return findRepository(entityType).findEntity(entityTypeId);
    }

    public CHSEntity getEntityType(String entityType, String entityTypeUuid) {
        return findRepository(entityType).findByUuid(entityTypeUuid);
    }

    private CHSRepository findRepository(String entityType) {
        switch (entityType) {
            case "Subject":
                return subjectTypeRepository;
            case "ProgramEnrolment":
                return programRepository;
            case "Encounter":
            case "ProgramEncounter":
                return encounterTypeRepository;
            default:
                throw new IllegalArgumentException("Unknown entityType " + entityType);
        }
    }

    public PrivilegeType findPrivilegeType(String entityType) {
        switch (entityType) {
            case "Subject":
                return PrivilegeType.EditSubjectType;
            case "ProgramEnrolment":
                return PrivilegeType.EditProgram;
            case "Encounter":
            case "ProgramEncounter":
                return PrivilegeType.EditEncounterType;
            case "User":
                return PrivilegeType.EditOrganisationConfiguration;
            default:
                throw new IllegalArgumentException("Unknown entityType " + entityType);
        }
    }
}
