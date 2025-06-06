package org.avni.server.dao.program;

import jakarta.persistence.criteria.*;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SubjectTreeItemRepository;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.TransactionalDataRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.program.SubjectProgramEligibility;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static org.avni.server.dao.sync.TransactionDataCriteriaBuilderUtil.joinUserSubjectAssignment;

@Repository
@RepositoryRestResource(collectionResourceRel = "subjectProgramEligibility", path = "subjectProgramEligibility", exported = false)
public interface SubjectProgramEligibilityRepository extends TransactionalDataRepository<SubjectProgramEligibility>, OperatingIndividualScopeAwareRepository<SubjectProgramEligibility>, SubjectTreeItemRepository {

    default Specification<SubjectProgramEligibility> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<SubjectProgramEligibility> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            SubjectType subjectType = syncParameters.getSubjectType();
            Join<SubjectProgramEligibility, Individual> subjectJoin = root.join("subject");
            predicates.add(cb.equal(subjectJoin.get("subjectType").get("id"), syncParameters.getTypeId()));
            if (subjectType.isShouldSyncByLocation()) {
                List<Long> addressLevels = syncParameters.getAddressLevels();
                if (addressLevels.size() > 0) {
                    CriteriaBuilder.In<Long> addressLevelFilter = cb.in(subjectJoin.get("addressLevel").get("id"));
                    for (Long id : addressLevels) {
                        addressLevelFilter.value(id);
                    }
                    predicates.add(addressLevelFilter);
                } else {
                    predicates.add(cb.equal(root.get("id"), cb.literal(0)));
                }
            }
            if (subjectType.isDirectlyAssignable()) {
                User user = UserContextHolder.getUserContext().getUser();
                Join<Object, Object> userSubjectAssignmentJoin = joinUserSubjectAssignment(subjectJoin);
                predicates.add(cb.equal(userSubjectAssignmentJoin.get("user"), user));
                predicates.add(cb.equal(userSubjectAssignmentJoin.get("isVoided"), false));
            }
            addSyncAttributeConceptPredicate(cb, predicates, subjectJoin, syncParameters, "syncConcept1Value", "syncConcept2Value");
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncStrategySpecification(syncParameters))
                .and(syncDisabledSpecification())
        ) > 0;
    }

    @Modifying
    @Query(value = "update subject_program_eligibility e set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById " +
            "from individual i" +
            " where i.address_id = :addressId and i.id = e.subject_id and e.is_voided = false", nativeQuery = true)
    void voidSubjectItemsAt(Long addressId, Long lastModifiedById);
    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectItemsAt(address.getId(), UserContextHolder.getUserId());
    }
}

