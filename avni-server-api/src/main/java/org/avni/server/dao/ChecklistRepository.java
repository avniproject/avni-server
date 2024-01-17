package org.avni.server.dao;

import org.avni.server.domain.Checklist;
import org.avni.server.domain.ChecklistDetail;
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
@RepositoryRestResource(collectionResourceRel = "txNewChecklistEntity", path = "txNewChecklistEntity", exported = false)
public interface ChecklistRepository extends TransactionalDataRepository<Checklist>, OperatingIndividualScopeAwareRepository<Checklist>, SubjectTreeItemRepository {

    Page<Checklist> findByProgramEnrolmentIndividualAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            long catchmentId, Date lastModifiedDateTime, Date now, Pageable pageable);

    Checklist findByProgramEnrolmentId(long programEnrolmentId);

    Set<Checklist> findByProgramEnrolmentIndividual(Individual individual);

    Checklist findByProgramEnrolmentUuidAndChecklistDetailName(String enrolmentUUID, String name);

    Checklist findFirstByChecklistDetail(ChecklistDetail checklistDetail);

    default Specification<Checklist> syncStrategySpecification(SyncParameters syncParameters) {
        return (Root<Checklist> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Checklist, ProgramEnrolment> programEnrolmentJoin = root.join("programEnrolment");
            predicates.add(cb.equal(root.get("checklistDetail").get("id"), syncParameters.getTypeId()));
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
    @Query(value = "update checklist c set last_modified_date_time = (current_timestamp + c.id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById" +
            " from program_enrolment pe where pe.id = c.program_enrolment_id and pe.individual_id = :individualId", nativeQuery = true)
    void setChangedForSync(Long individualId, Long lastModifiedById);
    default void setChangedForSync(Individual individual) {
        this.setChangedForSync(individual.getId(), UserContextHolder.getUserId());
    }

    @Override
    default void voidSubjectsAt(Long addressId) {
    }
}
