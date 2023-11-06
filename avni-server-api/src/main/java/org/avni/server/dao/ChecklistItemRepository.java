package org.avni.server.dao;

import org.avni.server.domain.Checklist;
import org.avni.server.domain.ChecklistItem;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEnrolment;
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

@Repository
@RepositoryRestResource(collectionResourceRel = "txNewChecklistItemEntity", path = "txNewChecklistItemEntity", exported = false)
public interface ChecklistItemRepository extends TransactionalDataRepository<ChecklistItem>, OperatingIndividualScopeAwareRepository<ChecklistItem> {

    Page<ChecklistItem> findByChecklistProgramEnrolmentIndividualAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            long catchmentId, Date lastModifiedDateTime, Date now, Pageable pageable);

    ChecklistItem findByChecklistUuidAndChecklistItemDetailUuid(String checklistUUID, String checklistItemDetailUUID);

    Set<ChecklistItem> findByChecklistProgramEnrolmentIndividual(Individual individual);

    default Specification<ChecklistItem> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<ChecklistItem> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<ChecklistItem, Checklist> checklistJoin = root.join("checklist", JoinType.LEFT);
            Join<Checklist, ProgramEnrolment> programEnrolmentJoin = checklistJoin.join("programEnrolment");
            predicates.add(cb.equal(checklistJoin.get("checklistDetail").get("id"), syncParameters.getTypeId()));
            addSyncStrategyPredicates(syncParameters, cb, predicates, programEnrolmentJoin, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters){
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncStrategySpecification(syncParameters))
        ) > 0;
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update checklist_item ci set last_modified_date_time = :lastModifiedDateTime, last_modified_by_id = :lastModifiedById" +
            " from program_enrolment pe, checklist c where c.id = ci.checklist_id and pe.id = c.program_enrolment_id and pe.individual_id = :individualId", nativeQuery = true)
    void setChangedForSync(Long individualId, Date lastModifiedDateTime, Long lastModifiedById);
    default void setChangedForSync(Individual individual) {
        this.setChangedForSync(individual.getId(), new Date(), UserContextHolder.getUserId());
    }
}
