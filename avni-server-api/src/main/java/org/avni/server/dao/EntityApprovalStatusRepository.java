package org.avni.server.dao;

import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "entityApprovalStatus", path = "entityApprovalStatus")
public interface EntityApprovalStatusRepository extends TransactionalDataRepository<EntityApprovalStatus>, FindByLastModifiedDateTime<EntityApprovalStatus>,
        OperatingIndividualScopeAwareRepository<EntityApprovalStatus> {

    @PreAuthorize("hasAnyAuthority('user')")
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<EntityApprovalStatus> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);

    List<EntityApprovalStatus> findByEntityIdAndEntityTypeAndIsVoidedFalse(Long entityId, EntityApprovalStatus.EntityType entityType);
    EntityApprovalStatus findFirstByEntityIdAndEntityTypeAndIsVoidedFalseOrderByStatusDateTimeDesc(Long entityId, EntityApprovalStatus.EntityType entityType);

    @Override
    default Specification<EntityApprovalStatus> syncTypeIdSpecification(String uuid, SyncEntityName syncEntityName) {
        return (Root<EntityApprovalStatus> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("entityTypeUuid"), uuid));
            predicates.add(cb.equal(root.get("entityType"), EntityApprovalStatus.EntityType.valueOf(syncEntityName.name())));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters){
        return true;
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update entity_approval_status eas set " +
            "address_id = :addressId, " +
            "sync_concept_1_value = :syncAttribute1Value, " +
            "sync_concept_2_value = :syncAttribute2Value, " +
            "last_modified_date_time = :lastModifiedDateTime, last_modified_by_id = :lastModifiedById " +
            "where eas.individual_id = :individualId", nativeQuery = true)
    void updateSyncAttributesForIndividual(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value, Date lastModifiedDateTime, Long lastModifiedById);

    default void updateSyncAttributesForIndividual(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value) {
        this.updateSyncAttributesForIndividual(individualId, addressId, syncAttribute1Value, syncAttribute2Value, new Date(), UserContextHolder.getUserId());
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update entity_approval_status eas set " +
            "sync_concept_1_value = CAST((i.observations ->> CAST(:syncAttribute1 as text)) as text), " +
            "sync_concept_2_value = CAST((i.observations ->> CAST(:syncAttribute2 as text)) as text), " +
            "last_modified_date_time = :lastModifiedDateTime, last_modified_by_id = :lastModifiedById " +
            "from individual i " +
            "where eas.individual_id = i.id and i.subject_type_id = :subjectTypeId", nativeQuery = true)
    void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2, Date lastModifiedDateTime, Long lastModifiedById);
    default void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2) {
        this.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncAttribute1, syncAttribute2, new Date(), UserContextHolder.getUserId());
    }

    default EntityApprovalStatus saveEAS(EntityApprovalStatus entityToSave) {
        EntityApprovalStatus latestEAS = this.findFirstByEntityIdAndEntityTypeAndIsVoidedFalseOrderByStatusDateTimeDesc(entityToSave.getEntityId(), entityToSave.getEntityType());
        if (latestEAS != null && latestEAS.getApprovalStatus().getStatus().equals(entityToSave.getApprovalStatus().getStatus()))
            throw new RuntimeException(String.format("The latest approval for this entity has the same latest status. %s %s", entityToSave.getEntityType(), entityToSave.getEntityId()));
        return this.save(entityToSave);
    }
}
