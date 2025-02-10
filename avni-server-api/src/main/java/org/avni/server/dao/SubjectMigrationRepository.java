package org.avni.server.dao;

import jakarta.persistence.criteria.*;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.JsonObjectUtil;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface SubjectMigrationRepository extends TransactionalDataRepository<SubjectMigration>, OperatingIndividualScopeAwareRepository<SubjectMigration>, SubjectTreeItemRepository {
    default Specification<SubjectMigration> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<SubjectMigration> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> andPredicates = new ArrayList<>();

            //Subject Type
            SubjectType subjectType = syncParameters.getSubjectType();
            Join<SubjectMigration, Individual> individualJoin = root.join("individual", JoinType.LEFT);
            andPredicates.add(cb.equal(individualJoin.get("subjectType").get("id"), syncParameters.getTypeId()));

            //Sync Concepts
            JsonObject syncSettings = syncParameters.getSyncSettings();
            boolean syncConceptUsable = subjectType.isSyncRegistrationConcept1Usable() != null && subjectType.isSyncRegistrationConcept1Usable();
            if (syncConceptUsable) {
                Predicate predicate = getSyncConceptPredicate(root, cb, subjectType, syncSettings, "newSyncConcept1Value", "oldSyncConcept1Value", User.SyncSettingKeys.syncAttribute1);
                andPredicates.add(predicate);
            }
            syncConceptUsable = subjectType.isSyncRegistrationConcept2Usable() != null && subjectType.isSyncRegistrationConcept2Usable();
            if (syncConceptUsable) {
                Predicate predicate = getSyncConceptPredicate(root, cb, subjectType, syncSettings, "newSyncConcept2Value", "oldSyncConcept2Value", User.SyncSettingKeys.syncAttribute2);
                andPredicates.add(predicate);
            }

            //Address Levels
            List<Predicate> addressLevelPredicates = new ArrayList<>();
            if (subjectType.isShouldSyncByLocation()) {
                List<Long> addressLevels = syncParameters.getAddressLevels();
                if (addressLevels.size() > 0) {
                    CriteriaBuilder.In<Long> inClause1 = cb.in(root.get("oldAddressLevel").get("id"));
                    for (Long id : addressLevels) {
                        inClause1.value(id);
                    }
                    addressLevelPredicates.add(inClause1);
                } else {
                    addressLevelPredicates.add(cb.equal(root.get("id"), cb.literal(0)));
                }
            }
            Predicate addressLevelPredicate = cb.or(addressLevelPredicates.toArray(new Predicate[0]));

            andPredicates.add(addressLevelPredicate);
            return cb.and(andPredicates.toArray(new Predicate[0]));
        };
    }

    default Predicate getSyncConceptPredicate(Root<SubjectMigration> root, CriteriaBuilder cb, SubjectType subjectType, JsonObject syncSettings, String newSyncConceptName, String oldSyncConceptName, User.SyncSettingKeys syncAttribute) {
        List<String> syncConceptValues = JsonObjectUtil.getSyncAttributeValuesBySubjectTypeUUID(syncSettings, subjectType.getUuid(), syncAttribute);
        if (syncConceptValues.size() == 0) {
            return cb.isTrue(cb.literal(false));
        } else {
            List<Predicate> predicateList = new ArrayList<>();
            CriteriaBuilder.In<Object> newSyncConceptValue = cb.in(root.get(newSyncConceptName));
            CriteriaBuilder.In<Object> oldSyncConceptValue = cb.in(root.get(oldSyncConceptName));
            for (String value : syncConceptValues) {
                newSyncConceptValue.value(value);
                oldSyncConceptValue.value(value);
            }
            predicateList.add(newSyncConceptValue);
            predicateList.add(oldSyncConceptValue);

            predicateList.add(cb.isNull(root.get(newSyncConceptName)));
            predicateList.add(cb.isNull(root.get(oldSyncConceptName)));
            return cb.or(predicateList.toArray(new Predicate[0]));
        }
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncStrategySpecification(syncParameters))
        ) > 0;
    }

    @Modifying
    @Query(value = "update subject_migration e set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById " +
            "from individual i" +
            " where i.address_id = :addressId and i.id = e.individual_id and e.is_voided = false", nativeQuery = true)
    void voidSubjectItemsAt(Long addressId, Long lastModifiedById);
    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectItemsAt(address.getId(), UserContextHolder.getUserId());
    }
}
