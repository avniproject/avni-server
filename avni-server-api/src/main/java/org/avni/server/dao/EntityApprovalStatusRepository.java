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

    default Specification<EntityApprovalStatus> syncTypeIdSpecification(String uuid, SyncParameters.SyncEntityName syncEntityName) {
        return (Root<EntityApprovalStatus> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("entityTypeUuid"), uuid));
            predicates.add(cb.equal(root.get("entityType"), EntityApprovalStatus.EntityType.valueOf(syncEntityName.name())));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    default Page<EntityApprovalStatus> getSyncResults(SyncParameters syncParameters) {
        return findAll(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncTypeIdSpecification(syncParameters.getEntityTypeUuid(), syncParameters.getSyncEntityName()))
                .and(syncStrategySpecification(syncParameters)), syncParameters.getPageable());
    }

    @Override
    default boolean isEntityChangedForCatchment(SyncParameters syncParameters){
        return true;
    }
}
