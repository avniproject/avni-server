package org.avni.server.dao;

import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.dao.sync.TransactionDataCriteriaBuilderUtil;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.JsonObjectUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@NoRepositoryBean
public interface OperatingIndividualScopeAwareRepository<T extends CHSEntity> extends JpaSpecificationExecutor<T>, CustomJpaRepository<T, Long> {
    default Specification getSpecification(SyncParameters syncParameters) {
        Specification specification = syncAuditSpecification(syncParameters)
                .and(syncStrategySpecification(syncParameters));

        if (syncParameters.getTypeId() != null)
            specification = specification.and(syncTypeIdSpecification(syncParameters.getTypeId()));
        if (syncParameters.getSyncEntityName() != null && syncParameters.getEntityTypeUuid() != null)
            specification = specification.and(syncTypeIdSpecification(syncParameters.getEntityTypeUuid(), syncParameters.getSyncEntityName()));

        return specification;
    }

    default Slice<T> getSyncResultsAsSlice(SyncParameters syncParameters) {
        Specification specification = getSpecification(syncParameters);
        return findAllAsSlice(specification, syncParameters.getPageable());
    }

    default Page<T> getSyncResults(SyncParameters syncParameters) {
        Specification specification = getSpecification(syncParameters);
        return findAll(specification, syncParameters.getPageable());
    }

    boolean isEntityChanged(SyncParameters syncParameters);

    default Specification<T> syncAuditSpecification(SyncParameters syncParameters) {
        Date lastModifiedDateTime = syncParameters.getLastModifiedDateTime().toDate();
        Date now = syncParameters.getNow().toDate();
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("lastModifiedDateTime"), cb.literal(lastModifiedDateTime), cb.literal(now)));
            query.orderBy(cb.asc(root.get("lastModifiedDateTime")), cb.asc(root.get("id")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    default Specification<T> syncTypeIdSpecification(Long typeId) {
        return null;
    }

    default Specification<T> syncTypeIdSpecification(String uuid, SyncEntityName syncEntityName) {
        return null;
    }

    default Specification<T> syncEntityChangedAuditSpecification(SyncParameters syncParameters) {
        Date lastModifiedDateTime = syncParameters.getLastModifiedDateTime().toDate();
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThan(root.get("lastModifiedDateTime"), cb.literal(lastModifiedDateTime)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    default <A extends CHSEntity> Specification<A> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<A> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addSyncStrategyPredicates(syncParameters, cb, predicates, root);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    default <A extends CHSEntity, B extends CHSEntity> void addSyncStrategyPredicates(SyncParameters syncParameters,
                                                                                      CriteriaBuilder cb,
                                                                                      List<Predicate> predicates,
                                                                                      From<A, B> from) {
        SubjectType subjectType = syncParameters.getSubjectType();
        if (subjectType.isShouldSyncByLocation()) {
            List<Long> addressLevels = syncParameters.getAddressLevels();
            if (addressLevels.size() > 0) {
                CriteriaBuilder.In<Long> inClause;
                if (syncParameters.isParentOrSelfIndividual()) {
                    inClause = cb.in(from.get("addressLevel").get("id"));
                } else {
                    inClause = cb.in(from.get("addressId"));
                }
                for (Long id : addressLevels) {
                    inClause.value(id);
                }
                predicates.add(inClause);
            } else {
                predicates.add(cb.equal(from.get("id"), cb.literal(0)));
            }
        }
        User user = UserContextHolder.getUserContext().getUser();
        if (subjectType.isDirectlyAssignable()) {
            Join<Object, Object> userSubjectAssignmentJoin = null;
            if (syncParameters.isParentOrSelfIndividual()) {
                userSubjectAssignmentJoin = TransactionDataCriteriaBuilderUtil.joinUserSubjectAssignment(from);
            } else if (syncParameters.isProgramEncounter() ||
                    syncParameters.isEncounter() ||
                    syncParameters.isParentOrSelfEnrolment()) {
                userSubjectAssignmentJoin = TransactionDataCriteriaBuilderUtil.joinUserSubjectAssignmentViaSubject(from);
            }

            if (userSubjectAssignmentJoin != null) {
                predicates.add(cb.equal(userSubjectAssignmentJoin.get("user"), user));
                predicates.add(cb.equal(userSubjectAssignmentJoin.get("isVoided"), false));
            }
        }
        addSyncAttributeConceptPredicate(cb, predicates, from, syncParameters, "syncConcept1Value", "syncConcept2Value");
    }

    default <A extends CHSEntity, B extends CHSEntity> void addSyncAttributeConceptPredicate(CriteriaBuilder cb,
                                                                                             List<Predicate> predicates,
                                                                                             From<A, B> from,
                                                                                             SyncParameters syncParameters,
                                                                                             String syncConcept1Column,
                                                                                             String syncConcept2Column) {
        SubjectType subjectType = syncParameters.getSubjectType();
        JsonObject syncSettings = syncParameters.getSyncSettings();
        Boolean isSyncRegistrationConcept1Usable = subjectType.isSyncRegistrationConcept1Usable();
        Boolean isSyncRegistrationConcept2Usable = subjectType.isSyncRegistrationConcept2Usable();
        if (isSyncRegistrationConcept1Usable != null && isSyncRegistrationConcept1Usable) {
            List<String> syncConcept1Values = JsonObjectUtil.getSyncAttributeValuesBySubjectTypeUUID(syncSettings, subjectType.getUuid(), User.SyncSettingKeys.syncAttribute1);
            addPredicate(cb, predicates, from, syncConcept1Values, syncConcept1Column);
        }
        if (isSyncRegistrationConcept2Usable != null && isSyncRegistrationConcept2Usable) {
            List<String> syncConcept2Values = JsonObjectUtil.getSyncAttributeValuesBySubjectTypeUUID(syncSettings, subjectType.getUuid(), User.SyncSettingKeys.syncAttribute2);
            addPredicate(cb, predicates, from, syncConcept2Values, syncConcept2Column);
        }
    }

    default <B extends CHSEntity, A extends CHSEntity> void addPredicate(CriteriaBuilder cb, List<Predicate> predicates, From<A, B> from, List<String> conceptValues, String syncAttributeColumn) {
        if (conceptValues.size() > 0) {
            CriteriaBuilder.In<Object> inClause = cb.in(from.get(syncAttributeColumn));
            for (String value : conceptValues) {
                inClause.value(value);
            }
            predicates.add(inClause);
        } else {
            predicates.add(cb.equal(from.get("id"), cb.literal(0)));
        }
    }
}
