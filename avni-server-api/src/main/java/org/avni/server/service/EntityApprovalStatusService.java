package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.web.request.EntityApprovalStatusRequest;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.EntityApprovalStatusWrapper;
// The checked domain ValidationException, not web.validation's unchecked one - this is the exception
// ObservationService.validateObservationsAndDecisions declares, and the one IndividualController and
// EncounterController propagate for exactly the same failure.
import org.avni.server.domain.ValidationException;
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
    private final EntityApprovalStatusRepository entityApprovalStatusRepository;
    private final ApprovalStatusRepository approvalStatusRepository;
    private final ObservationService observationService;
    private final FormMappingRepository formMappingRepository;
    private final Map<EntityApprovalStatus.EntityType, TransactionalDataRepository> typeMap = new HashMap<>();

    @Autowired
    public EntityApprovalStatusService(EntityApprovalStatusRepository entityApprovalStatusRepository, ApprovalStatusRepository approvalStatusRepository, IndividualRepository individualRepository, EncounterRepository encounterRepository, ChecklistItemRepository checklistItemRepository, ProgramEncounterRepository programEncounterRepository, ProgramEnrolmentRepository programEnrolmentRepository, ObservationService observationService, FormMappingRepository formMappingRepository) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.approvalStatusRepository = approvalStatusRepository;
        this.observationService = observationService;
        this.formMappingRepository = formMappingRepository;
        this.typeMap.put(Subject, individualRepository);
        this.typeMap.put(Encounter, encounterRepository);
        this.typeMap.put(ChecklistItem, checklistItemRepository);
        this.typeMap.put(ProgramEncounter, programEncounterRepository);
        this.typeMap.put(ProgramEnrolment, programEnrolmentRepository);
    }

    public void save(EntityApprovalStatusRequest request) throws ValidationException {
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
        ApprovalStatus approvalStatus = approvalStatusRepository.findByUuid(request.getApprovalStatusUuid());
        entityApprovalStatus.setUuid(request.getUuid());
        entityApprovalStatus.setApprovalStatus(approvalStatus);
        entityApprovalStatus.setApprovalStatusComment(request.getApprovalStatusComment());
        if (request.getObservations() != null) {
            validateAnswersAgainstDecisionForm(request.getObservations(), approvalStatus, entityType, entity);
            entityApprovalStatus.setObservations(toObservations(request.getObservations()));
        }
        entityApprovalStatus.setVoided(request.isVoided());
        entityApprovalStatus.setEntityType(entityType);
        entityApprovalStatus.setAutoApproved(request.getAutoApproved());
        entityApprovalStatus.setStatusDateTime(request.getStatusDateTime());
        entityApprovalStatus.updateAudit();
        entityApprovalStatus.setEntityId(entity.getId());

        CHSEntity chsEntity = this.typeMap.get(entityType).findByUuid(request.getEntityUuid());
        updateIndividualAndSyncAttributes(chsEntity, entityApprovalStatus, entityType);
        entityApprovalStatusRepository.saveEAS(entityApprovalStatus);
    }

    /**
     * Answers on an approval decision are validated against the form they were captured on, the same way
     * every other observation write in the codebase validates against its form mapping. Without this the
     * approval path was the only one that accepted a coded answer outside the concept's answer set, or a
     * string in a Numeric question, and surfaced it later as bad jsonb in reporting.
     *
     * An empty list is not validated: it carries nothing to check and its meaning is "clear the stored
     * answers". The caller has already separated it from an absent key, which means "leave them alone".
     * Neither case needs a form attached, which is what keeps every client released before this feature
     * working unchanged - they send no observations key at all.
     *
     * The whole check sits behind AVNI_ENHANCED_VALIDATION, the switch every other observation write
     * already honours. A deployment that has turned validation off gets the previous behaviour here too,
     * rather than a refusal it has no way to opt out of.
     */
    private void validateAnswersAgainstDecisionForm(List<ObservationRequest> observationRequests, ApprovalStatus approvalStatus,
                                                    EntityApprovalStatus.EntityType entityType, CHSEntity entity) throws ValidationException {
        if (observationRequests.isEmpty() || !observationService.isEnhancedValidationEnabled()) {
            return;
        }
        FormType decisionFormType = decisionFormTypeFor(approvalStatus.getStatus());
        if (decisionFormType == null) {
            throw new ValidationException(String.format(
                    "Cannot record answers against a %s decision. Answers are only captured when a record is " +
                            "approved or rejected.", approvalStatus.getStatus()));
        }
        FormMapping decisionFormMapping = findDecisionFormMapping(entityType, entity, decisionFormType);
        if (decisionFormMapping == null) {
            throw new ValidationException(String.format(
                    "Cannot record answers against this decision. No %s form is attached to this subject type, " +
                            "programme and visit type combination.", decisionFormType));
        }
        observationService.validateObservationsAndDecisions(observationRequests, null, decisionFormMapping);
    }

    /**
     * Pending is not a decision anyone fills a form for - it is the state a record sits in while it waits
     * for one - so it has no decision form and answers arriving on it are a client error.
     */
    private FormType decisionFormTypeFor(ApprovalStatus.Status status) {
        switch (status) {
            case Approved: return FormType.Approval;
            case Rejected: return FormType.Rejection;
            default: return null;
        }
    }

    /**
     * The decision form is attached to the same (subject type, programme, visit type) combination as the
     * form whose approval switch produced this decision (#1052), so the triple is rebuilt from the entity
     * under approval. It is taken from the entity rather than from the request's entityTypeUuid because
     * that field carries only one leg of the triple - the encounter type for an encounter, the programme
     * for an enrolment - and never the subject type those hang off.
     *
     * ChecklistItem has no such triple. It is the one approval entity type with no form mapping of its
     * own, so a decision on one cannot carry validated answers, and returning null here refuses them
     * rather than storing them unchecked.
     */
    private FormMapping findDecisionFormMapping(EntityApprovalStatus.EntityType entityType, CHSEntity entity, FormType decisionFormType) {
        if (entityType == ChecklistItem) {
            return null;
        }
        SubjectType subjectType = getIndividual(entityType, entity).getSubjectType();
        Program program = null;
        EncounterType encounterType = null;
        switch (entityType) {
            case ProgramEnrolment:
                program = ((org.avni.server.domain.ProgramEnrolment) entity).getProgram();
                break;
            case Encounter:
                encounterType = ((Encounter) entity).getEncounterType();
                break;
            case ProgramEncounter:
                program = ((ProgramEncounter) entity).getProgramEnrolment().getProgram();
                encounterType = ((ProgramEncounter) entity).getEncounterType();
                break;
            default:
                break;
        }
        return formMappingRepository.findDecisionFormMappingForCombination(
                subjectType.getId(), idOf(program), idOf(encounterType), decisionFormType);
    }

    private Long idOf(CHSEntity entity) {
        return entity == null ? null : entity.getId();
    }

    /**
     * A decision that carries no answers stores SQL NULL, not {}.
     *
     * Two reasons the conversion is guarded rather than calling ObservationService directly the way
     * ProgramEncounterService does. An empty ObservationCollection is written as {} by
     * ObservationCollectionUserType, which would change the stored shape for every client released
     * before the approval/rejection form. And createObservations drops entries, so a list of skipped
     * questions arrives here having retained nothing. One rule covers both: an answers list that
     * retains no entries stores NULL.
     *
     * "Retains no entries" is narrower than "the approver left the form blank", and the filter doing
     * the dropping is broader than it looks. ObservationService:73-74 discards any entry whose value
     * stringifies to "null", case-insensitively:
     *
     *     !"null".equalsIgnoreCase(String.valueOf(obsReqAsMap.getValue()))
     *
     * So a literal null goes, but so does an approver who types "null", "NULL" or "Null" as a
     * rejection note - that answer is silently lost, and if it was the only one, this method returns
     * null and a re-post of the same decision erases whatever was already stored. A text question
     * cleared to an empty string is kept, and stored as {"<concept uuid>": ""}. The filter is shared
     * by every entity and is not this path's to change; it is described accurately here because the
     * next maintainer and any report author reading `observations is null` depends on it.
     *
     * The caller checks for a null list separately, and that check is load-bearing: save() is an
     * upsert, so an absent observations key must leave answers that are already stored alone rather
     * than erasing them.
     */
    private ObservationCollection toObservations(List<ObservationRequest> observationRequests) {
        if (observationRequests.isEmpty()) {
            return null;
        }
        ObservationCollection observations = observationService.createObservations(observationRequests);
        return observations.isEmpty() ? null : observations;
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
        entityApprovalStatusRepository.saveEAS(entityApprovalStatus);
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
