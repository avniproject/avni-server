package org.avni.server.dao;

import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectMigration;
import org.avni.server.domain.SubjectType;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public interface SubjectMigrationRepository extends TransactionalDataRepository<SubjectMigration>, OperatingIndividualScopeAwareRepository<SubjectMigration> {

    default Specification<SubjectMigration> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<SubjectMigration> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> andPredicates = new ArrayList<>();
            SubjectType subjectType = syncParameters.getSubjectType();
            Join<SubjectMigration, Individual> individualJoin = root.join("individual", JoinType.LEFT);
            andPredicates.add(cb.equal(individualJoin.get("subjectType").get("id"), syncParameters.getTypeId()));

            List<Predicate> orPredicates = new ArrayList<>();

            List<Predicate> newConceptPredicates = new ArrayList<>();
            addSyncAttributeConceptPredicate(cb, newConceptPredicates, root, syncParameters, "newSyncConcept1Value", "newSyncConcept2Value");
            Predicate newConceptPredicate = cb.or(newConceptPredicates.toArray(new Predicate[0]));
            orPredicates.add(newConceptPredicate);

            List<Predicate> oldConceptPredicates = new ArrayList<>();
            addSyncAttributeConceptPredicate(cb, oldConceptPredicates, root, syncParameters, "oldSyncConcept1Value", "oldSyncConcept2Value");
            Predicate oldConceptPredicate = cb.or(oldConceptPredicates.toArray(new Predicate[0]));
            orPredicates.add(oldConceptPredicate);

            if (subjectType.isShouldSyncByLocation()) {
                List<Long> addressLevels = syncParameters.getAddressLevels();
                if (addressLevels.size() > 0) {
                    CriteriaBuilder.In<Long> inClause1 = cb.in(root.get("oldAddressLevel").get("id"));
                    CriteriaBuilder.In<Long> inClause2 = cb.in(root.get("newAddressLevel").get("id"));
                    for (Long id : addressLevels) {
                        inClause1.value(id);
                        inClause2.value(id);
                    }
                    orPredicates.add(inClause1);
                    orPredicates.add(inClause2);
                } else {
                    orPredicates.add(cb.equal(root.get("id"), cb.literal(0)));
                }
            }
            Predicate orPredicate = cb.or(orPredicates.toArray(new Predicate[0]));

            andPredicates.add(orPredicate);
            return cb.and(andPredicates.toArray(new Predicate[0]));
        };
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncStrategySpecification(syncParameters))
        ) > 0;
    }
}
