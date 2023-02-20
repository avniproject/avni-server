package org.avni.server.dao;

import org.avni.server.domain.EntityApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "entityApprovalStatus", path = "entityApprovalStatus")
@PreAuthorize("hasAnyAuthority('user', 'admin')")
public interface EntityApprovalStatusRepository extends TransactionalDataRepository<EntityApprovalStatus>, FindByLastModifiedDateTime<EntityApprovalStatus>,
        OperatingIndividualScopeAwareRepository<EntityApprovalStatus> {
    EntityApprovalStatus findFirstByEntityIdAndEntityTypeAndIsVoidedFalseOrderByStatusDateTimeDesc(Long entityId, EntityApprovalStatus.EntityType entityType);

    default Specification<EntityApprovalStatus> syncTypeIdSpecification(Long typeId, SyncParameters.SyncEntityName syncEntityName) {
        return (Root<EntityApprovalStatus> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("entityTypeId"), typeId));
            predicates.add(cb.equal(root.get("entityTypeName"), syncEntityName.name()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

//    @Override
//    default <A extends CHSEntity, B extends CHSEntity> void addSyncStrategyPredicates(SyncParameters syncParameters,
//                                                                                      CriteriaBuilder cb,
//                                                                                      List<Predicate> predicates,
//                                                                                      From<A, B> from) {
//        SyncParameters.SyncEntityName syncEntityName = syncParameters.getSyncEntityName();
//        SubjectType subjectType = syncParameters.getSubjectType();
//        if (subjectType.isShouldSyncByLocation()) {
//            List<Long> addressLevels = syncParameters.getAddressLevels();
//            if (addressLevels.size() > 0) {
//                /**
//                 * eas join subjectType st on st.id = eas.entityTypeId
//                 * join individual ind on ind.subjectType.id = st.id
//                 * where eas.entityType = 'Subject'
//                 *  and ind.addressLevel in (addressLevels..)
//                 *  and st.id = syncParameters.subjectType.id
//                 */
//
////                if(syncEntityName.equals(SyncParameters.SyncEntityName.Subject))
//                Join<EntityApprovalStatus, Individual> individualJoin = from.join("individual");
//                Predicate stClause = cb.equal(individualJoin.get("subjectType").get("uuid"),
//                        syncParameters.getSubjectType().getUuid());
//                CriteriaBuilder.In<Long> inClause = cb.in(individualJoin.join("individual").get("addressId"));
//                for (Long id : addressLevels) {
//                    inClause.value(id);
//                }
//                predicates.add();
//                predicates.add(inClause);
//            } else {
//                predicates.add(cb.equal(from.get("id"), cb.literal(0)));
//            }
//        }
//        User user = UserContextHolder.getUserContext().getUser();
//        if (subjectType.isDirectlyAssignable()) {
//            Join<Object, Object> userSubjectAssignmentJoin = null;
//            if (syncParameters.isParentOrSelfIndividual()) {
//                userSubjectAssignmentJoin = TransactionDataCriteriaBuilderUtil.joinUserSubjectAssignment(from);
//            } else if (syncParameters.isProgramEncounter() ||
//                    syncParameters.isEncounter() ||
//                    syncParameters.isParentOrSelfEnrolment()) {
//                userSubjectAssignmentJoin = TransactionDataCriteriaBuilderUtil.joinUserSubjectAssignmentViaSubject(from);
//            }
//
//            if (userSubjectAssignmentJoin != null) {
//                predicates.add(cb.equal(userSubjectAssignmentJoin.get("user"), user));
//                predicates.add(cb.equal(userSubjectAssignmentJoin.get("isVoided"), false));
//            }
//        }
//        addSyncAttributeConceptPredicate(cb, predicates, from, syncParameters, "syncConcept1Value", "syncConcept2Value");
//    }

    @Override
    default Page<EntityApprovalStatus> getSyncResults(SyncParameters syncParameters) {
        return findAll(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncTypeIdSpecification(syncParameters.getTypeId(), syncParameters.getSyncEntityName()))
                .and(syncStrategySpecification(syncParameters)), syncParameters.getPageable());
    }

    @Override
    default boolean isEntityChangedForCatchment(SyncParameters syncParameters){
        return true;
    }


}
