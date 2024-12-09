package org.avni.server.dao;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.joda.time.DateTime;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "entityApprovalStatus", path = "entityApprovalStatus")
public interface EntityApprovalStatusRepository extends TransactionalDataRepository<EntityApprovalStatus>, FindByLastModifiedDateTime<EntityApprovalStatus>,
        OperatingIndividualScopeAwareRepository<EntityApprovalStatus>, SubjectTreeItemRepository {

    @PreAuthorize("hasAnyAuthority('user')")
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<EntityApprovalStatus> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant now,
            Pageable pageable);

    default Page<EntityApprovalStatus> findEntityApprovalStatuses(EntityApprovalStatusSearchParams searchParams, Pageable pageable) {

        Specification specification = lastModifiedBetween(
            CHSEntity.toDate(searchParams.getLastModifiedDateTime() != null ? searchParams.getLastModifiedDateTime() : EntitySyncStatusContract.REALLY_OLD_DATE),
            CHSEntity.toDate(searchParams.getNow() != null ? searchParams.getNow() : new DateTime()));

        if (searchParams.getEntityType() != null) {
            specification = specification.and(findByEntityTypeSpec(searchParams.getEntityType()));
        }
        if (searchParams.getEntityTypeUuid() != null) {
            specification = specification.and(findByEntityTypeUuidSpec(searchParams.getEntityTypeUuid()));
        }

        return findAll(specification, pageable);
    }

    default Specification<EntityApprovalStatus> findByEntityTypeSpec(EntityApprovalStatus.EntityType entityType) {
        Specification<EntityApprovalStatus> spec = (Root<EntityApprovalStatus> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            return cb.and(cb.equal(root.get("entityType"), entityType));
        };
        return spec;
    }

    default Specification<EntityApprovalStatus> findByEntityTypeUuidSpec(String entityTypeUuid) {
        Specification<EntityApprovalStatus> spec = (Root<EntityApprovalStatus> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            return cb.and(cb.equal(root.get("entityTypeUuid"), entityTypeUuid));
        };
        return spec;
    }
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
            "last_modified_date_time = (current_timestamp + id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "where eas.individual_id = :individualId", nativeQuery = true)
    void updateSyncAttributesForIndividual(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value, Long lastModifiedById);

    default void updateSyncAttributesForIndividual(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value) {
        this.updateSyncAttributesForIndividual(individualId, addressId, syncAttribute1Value, syncAttribute2Value, UserContextHolder.getUserId());
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update entity_approval_status eas set " +
            "sync_concept_1_value = CAST((i.observations ->> CAST(:syncAttribute1 as text)) as text), " +
            "sync_concept_2_value = CAST((i.observations ->> CAST(:syncAttribute2 as text)) as text), " +
            "last_modified_date_time = (current_timestamp + eas.id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "from individual i " +
            "where eas.individual_id = i.id and i.subject_type_id = :subjectTypeId", nativeQuery = true)
    void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2, Long lastModifiedById);
    default void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2) {
        this.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncAttribute1, syncAttribute2, UserContextHolder.getUserId());
    }

    default EntityApprovalStatus saveEAS(EntityApprovalStatus entityToSave) {
        EntityApprovalStatus latestEAS = this.findFirstByEntityIdAndEntityTypeAndIsVoidedFalseOrderByStatusDateTimeDesc(entityToSave.getEntityId(), entityToSave.getEntityType());
        if (latestEAS != null && latestEAS.getApprovalStatus().getStatus().equals(entityToSave.getApprovalStatus().getStatus())) {
            return null;
            // check the number clients on version < 6.1 before uncommenting
//            throw new RuntimeException(String.format("The latest approval for this entity has the same latest status. %s %s %s", entityToSave.getEntityType(), entityToSave.getEntityId(), entityToSave.getApprovalStatus().getStatus()));
        }
        return this.save(entityToSave);
    }

    @Modifying
    @Query(value = "update entity_approval_status e set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById " +
            "from individual i" +
            " where i.address_id = :addressId and i.id = e.individual_id and e.is_voided = false", nativeQuery = true)
    void voidSubjectsAt(Long addressId, Long lastModifiedById);
    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectsAt(address.getId(), UserContextHolder.getUserId());
    }
}
