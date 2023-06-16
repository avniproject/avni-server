package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.web.request.EntityApprovalStatusRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.EntityApprovalStatusWrapper;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.avni.server.domain.EntityApprovalStatus.EntityType.*;

@Service
public class EntityApprovalStatusService implements NonScopeAwareService {
    private EntityApprovalStatusRepository entityApprovalStatusRepository;
    private ApprovalStatusRepository approvalStatusRepository;
    private Map<EntityApprovalStatus.EntityType, TransactionalDataRepository> typeMap = new HashMap<>();

    @Autowired
    public EntityApprovalStatusService(EntityApprovalStatusRepository entityApprovalStatusRepository, ApprovalStatusRepository approvalStatusRepository, IndividualRepository individualRepository, EncounterRepository encounterRepository, ChecklistItemRepository checklistItemRepository, ProgramEncounterRepository programEncounterRepository, ProgramEnrolmentRepository programEnrolmentRepository) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.approvalStatusRepository = approvalStatusRepository;
        this.typeMap.put(Subject, individualRepository);
        this.typeMap.put(Encounter, encounterRepository);
        this.typeMap.put(ChecklistItem, checklistItemRepository);
        this.typeMap.put(ProgramEncounter, programEncounterRepository);
        this.typeMap.put(ProgramEnrolment, programEnrolmentRepository);
    }

    public EntityApprovalStatus save(EntityApprovalStatusRequest request) {
        EntityApprovalStatus entityApprovalStatus = entityApprovalStatusRepository.findByUuid(request.getUuid());
        if (entityApprovalStatus == null) {
            entityApprovalStatus = new EntityApprovalStatus();
        }
        EntityApprovalStatus.EntityType entityType = EntityApprovalStatus.EntityType.valueOf(request.getEntityType());
        CHSEntity entity = getChsEntity(entityType, request.getEntityUuid());
        if(StringUtils.hasText(request.getEntityTypeUuid())) {
            entityApprovalStatus.setEntityTypeUuid(request.getEntityTypeUuid());
        } else if(!StringUtils.hasText(entityApprovalStatus.getEntityTypeUuid())) {
            entityApprovalStatus.setEntityTypeUuid(getEntityTypeUUID(entityType, entity));
        }
        entityApprovalStatus.setUuid(request.getUuid());
        entityApprovalStatus.setApprovalStatus(approvalStatusRepository.findByUuid(request.getApprovalStatusUuid()));
        entityApprovalStatus.setApprovalStatusComment(request.getApprovalStatusComment());
        entityApprovalStatus.setVoided(request.isVoided());
        entityApprovalStatus.setEntityType(entityType);
        entityApprovalStatus.setAutoApproved(request.getAutoApproved());
        entityApprovalStatus.setStatusDateTime(request.getStatusDateTime());
        entityApprovalStatus.updateAudit();
        entityApprovalStatus.setEntityId(entity.getId());

        CHSEntity chsEntity = this.typeMap.get(entityType).findByUuid(request.getEntityUuid());
        updateIndividualAndSyncAttributes(chsEntity, entityApprovalStatus, entityType);
        return entityApprovalStatusRepository.save(entityApprovalStatus);
    }

    public void createStatus(EntityApprovalStatus.EntityType entityType, Long entityId, ApprovalStatus.Status status, String entityTypeUuid, FormMapping formMapping) {

        if (!formMapping.isEnableApproval()) {
            return;
        }

        ApprovalStatus approvalStatus = approvalStatusRepository.findByStatus(status);
        List<EntityApprovalStatus> entityApprovalStatuses = entityApprovalStatusRepository.findByEntityIdAndEntityTypeAndIsVoidedFalse(entityId, entityType);
        if (entityApprovalStatuses != null && entityApprovalStatuses.stream().anyMatch(x -> x.getApprovalStatus().getStatus().equals(status))) {
            return;
        }
        EntityApprovalStatus entityApprovalStatus = new EntityApprovalStatus();
        entityApprovalStatus.assignUUID();
        entityApprovalStatus.setEntityType(entityType);
        CHSEntity entity = getChsEntity(entityType, entityId);
        if(StringUtils.hasText(entityTypeUuid)) {
            entityApprovalStatus.setEntityTypeUuid(entityTypeUuid);
        } else if(!StringUtils.hasText(entityApprovalStatus.getEntityTypeUuid())) {
            entityApprovalStatus.setEntityTypeUuid(getEntityTypeUUID(entityType, entity));
        }
        entityApprovalStatus.setEntityId(entityId);
        entityApprovalStatus.setApprovalStatus(approvalStatus);
        entityApprovalStatus.setStatusDateTime(new DateTime());
        entityApprovalStatus.setAutoApproved(false);
        CHSEntity chsEntity = this.typeMap.get(entityType).findEntity(entityId);
        updateIndividualAndSyncAttributes(chsEntity, entityApprovalStatus, entityType);
        entityApprovalStatusRepository.save(entityApprovalStatus);
    }

    private void updateIndividualAndSyncAttributes(CHSEntity entity, EntityApprovalStatus entityApprovalStatus, EntityApprovalStatus.EntityType entityType) {
        Individual individual = getIndividual(entityType, entity);
        entityApprovalStatus.setIndividual(individual);
        entityApprovalStatus.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        if (individual.getAddressLevel() != null) {
            entityApprovalStatus.setAddressId(individual.getAddressLevel().getId());
        }
    }

    private Individual getIndividual(EntityApprovalStatus.EntityType entityType, CHSEntity chsEntity) {
        switch (entityType) {
            case Subject:
               return (Individual) chsEntity;
            case ProgramEnrolment:
                return ((org.avni.server.domain.ProgramEnrolment) chsEntity).getIndividual();
            case ProgramEncounter:
                return ((org.avni.server.domain.ProgramEncounter) chsEntity).getIndividual();
            case Encounter:
                return ((org.avni.server.domain.Encounter) chsEntity).getIndividual();
            case ChecklistItem:
                return ((org.avni.server.domain.ChecklistItem) chsEntity).getChecklist().getProgramEnrolment().getIndividual();
            default:
                throw new RuntimeException(String.format("Entity Approval Status is not supported for this entity type: %s", entityType));
        }
    }

    public String getEntityUuid(EntityApprovalStatus eaStatus) {
        CHSEntity entity = getChsEntity(eaStatus.getEntityType(), eaStatus.getEntityId());
        return entity.getUuid();
    }

    private CHSEntity getChsEntity(EntityApprovalStatus.EntityType entityType, Long entityId) {
        TransactionalDataRepository transactionalDataRepository = getTransactionalDataRepository(entityType);
        CHSEntity entity = transactionalDataRepository.findOne(entityId);
        if (entity == null) {
            throw new IllegalArgumentException(String.format("Incorrect entityId '%s' found in database while fetching EntityApprovalStatus", entityId));
        }
        return entity;
    }

    private CHSEntity getChsEntity(EntityApprovalStatus.EntityType entityType, String entityUUID) {
        TransactionalDataRepository transactionalDataRepository = getTransactionalDataRepository(entityType);
        CHSEntity entity = transactionalDataRepository.findByUuid(entityUUID);
        if (entity == null) {
            throw new IllegalArgumentException(String.format("Incorrect entityUUID '%s' found in database while fetching EntityApprovalStatus", entityUUID));
        }
        return entity;
    }

    private TransactionalDataRepository getTransactionalDataRepository(EntityApprovalStatus.EntityType entityType) {
        TransactionalDataRepository transactionalDataRepository = typeMap.get(entityType);
        if (transactionalDataRepository == null) {
            throw new IllegalArgumentException(String.format("Incorrect entityType '%s' found in database while fetching EntityApprovalStatus", entityType));
        }
        return transactionalDataRepository;
    }


    private String getEntityTypeUUID(EntityApprovalStatus.EntityType entityType, CHSEntity entity) {
        switch (entityType) {
            case Subject: return ((Individual)entity).getSubjectType().getUuid();
            case ProgramEnrolment: return ((org.avni.server.domain.ProgramEnrolment)entity).getProgram().getUuid();
            case ChecklistItem: return ((Checklist)entity).getProgramEnrolment().getProgram().getUuid();
            case Encounter: return ((Encounter)entity).getEncounterType().getUuid();
            case ProgramEncounter: return ((ProgramEncounter)entity).getEncounterType().getUuid();
            default: throw new IllegalArgumentException(String.format("Incorrect entityType '%s' not found", entityType));
        }
    }

    public List<EntityApprovalStatusWrapper> getEntityApprovalStatuses(Long entityId, EntityApprovalStatus.EntityType entityType, String entityUUID) {
        List<EntityApprovalStatus> entityApprovalStatuses = entityApprovalStatusRepository.findByEntityIdAndEntityTypeAndIsVoidedFalse(entityId, entityType);
        return entityApprovalStatuses.stream().map(entityApprovalStatus -> EntityApprovalStatusWrapper.fromEntity(entityApprovalStatus, entityUUID)).collect(Collectors.toList());
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return entityApprovalStatusRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

}
