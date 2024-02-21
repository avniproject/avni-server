package org.avni.server.dao.individualRelationship;

import org.avni.server.dao.*;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.avni.server.dao.sync.TransactionDataCriteriaBuilderUtil.joinUserSubjectAssignment;

@Repository
@RepositoryRestResource(collectionResourceRel = "individualRelationship", path = "individualRelationship", exported = false)
public interface IndividualRelationshipRepository extends TransactionalDataRepository<IndividualRelationship>, FindByLastModifiedDateTime<IndividualRelationship>, OperatingIndividualScopeAwareRepository<IndividualRelationship>, SubjectTreeItemRepository {
    Page<IndividualRelationship> findByIndividualaAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            long catchmentId, Date lastModifiedDateTime, Date now, Pageable pageable);

    @Query(value = "select ir from IndividualRelationship ir where ir.individuala = :individual or ir.individualB = :individual")
    Set<IndividualRelationship> findByIndividual(Individual individual);

    default Specification<IndividualRelationship> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<IndividualRelationship> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            SubjectType subjectType = syncParameters.getSubjectType();
            Join<IndividualRelationship, Individual> individualAJoin = root.join("individuala");
            Join<IndividualRelationship, Individual> individualBJoin = root.join("individualB");
            predicates.add(cb.equal(individualAJoin.get("subjectType").get("id"), syncParameters.getTypeId()));
            if (subjectType.isShouldSyncByLocation()) {
                List<Long> addressLevels = syncParameters.getAddressLevels();
                if (addressLevels.size() > 0) {
                    CriteriaBuilder.In<Long> inClause1 = cb.in(individualAJoin.get("addressLevel").get("id"));
                    CriteriaBuilder.In<Long> inClause2 = cb.in(individualBJoin.get("addressLevel").get("id"));
                    for (Long id : addressLevels) {
                        inClause1.value(id);
                        inClause2.value(id);
                    }
                    predicates.add(inClause1);
                    predicates.add(inClause2);
                } else {
                    predicates.add(cb.equal(root.get("id"), cb.literal(0)));
                }
            }
            if (subjectType.isDirectlyAssignable()) {
                User user = UserContextHolder.getUserContext().getUser();
                Join<Object, Object> userSubjectAAssignmentJoin = joinUserSubjectAssignment(individualAJoin);
                Join<Object, Object> userSubjectBAssignmentJoin = joinUserSubjectAssignment(individualBJoin);
                predicates.add(cb.equal(userSubjectAAssignmentJoin.get("user"), user));
                predicates.add(cb.equal(userSubjectAAssignmentJoin.get("isVoided"), false));
                predicates.add(cb.equal(userSubjectBAssignmentJoin.get("user"), user));
                predicates.add(cb.equal(userSubjectBAssignmentJoin.get("isVoided"), false));
            }
            addSyncAttributeConceptPredicate(cb, predicates, individualAJoin, syncParameters, "syncConcept1Value", "syncConcept2Value");
            addSyncAttributeConceptPredicate(cb, predicates, individualBJoin, syncParameters, "syncConcept1Value", "syncConcept2Value");
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncStrategySpecification(syncParameters))
        ) > 0;
    }

    List<IndividualRelationship> findByIndividualaAndIndividualBAndIsVoidedFalse(Individual individualA, Individual individualB);

    @Modifying(clearAutomatically = true)
    @Query(value = "update individual_relationship ir set last_modified_date_time = (current_timestamp + id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById" +
            " where ir.individual_a_id = :individualId or ir.individual_b_id = :individualId", nativeQuery = true)
    void setChangedForSync(Long individualId, Long lastModifiedById);
    default void setChangedForSync(Individual individual) {
        this.setChangedForSync(individual.getId(), UserContextHolder.getUserId());
    }

    @Modifying
    @Query(value = "update individual_relationship e set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById " +
            " from individual i" +
            " where i.address_id = :addressId and (i.id = e.individual_a_id or i.id = e.individual_b_id) and e.is_voided = false", nativeQuery = true)
    void voidSubjectItemsAt(Long addressId, Long lastModifiedById);
    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectItemsAt(address.getId(), UserContextHolder.getUserId());
    }
}
