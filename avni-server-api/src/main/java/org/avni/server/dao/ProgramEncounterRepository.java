package org.avni.server.dao;

import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.*;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import static org.springframework.data.jpa.domain.Specification.where;

@Repository
@RepositoryRestResource(collectionResourceRel = "programEncounter", path = "programEncounter", exported = false)
public interface ProgramEncounterRepository extends TransactionalDataRepository<ProgramEncounter>, FindByLastModifiedDateTime<ProgramEncounter>, OperatingIndividualScopeAwareRepository<ProgramEncounter>, SubjectTreeItemRepository {

    Page<ProgramEncounter> findByProgramEnrolmentIndividualAddressLevelVirtualCatchmentsIdAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            long catchmentId, Date lastModifiedDateTime, Date now, Pageable pageable);

    @Override
    default Specification<ProgramEncounter> syncTypeIdSpecification(Long typeId) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("encounterType").get("id"), typeId);
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters){
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncTypeIdSpecification(syncParameters.getTypeId()))
                .and(syncStrategySpecification(syncParameters))
        ) > 0;
    }

    @Query(value = "select count(enc.id) as count " +
            "from program_encounter enc " +
            "join encounter_type t on t.id = enc.encounter_type_id " +
            "where t.uuid = :programEncounterTypeUUID and (enc.encounter_date_time notnull or enc.cancel_date_time notnull) " +
            "and ((enc.encounter_date_time BETWEEN :startDate and :endDate) or (enc.cancel_date_time BETWEEN :startDate and :endDate)) " +
            "group by enc.program_enrolment_id " +
            "order by count desc " +
            "limit 1", nativeQuery = true)
    Long getMaxProgramEncounterCountBetween(String programEncounterTypeUUID, Calendar startDate, Calendar endDate);

    @Query(value = "select count(enc.id) as count " +
            "from program_encounter enc " +
            "join encounter_type t on t.id = enc.encounter_type_id " +
            "where t.uuid = :programEncounterTypeUUID and (enc.encounter_date_time notnull or enc.cancel_date_time notnull) " +
            "group by enc.program_enrolment_id " +
            "order by count desc " +
            "limit 1", nativeQuery = true)
    Long getMaxProgramEncounterCount(String programEncounterTypeUUID);

    default Long getMaxProgramEncounterCount(String encounterTypeUUID, Calendar startDate, Calendar endDate) {
        Long aLong = startDate == null ? getMaxProgramEncounterCount(encounterTypeUUID) :
                getMaxProgramEncounterCountBetween(encounterTypeUUID, startDate, endDate);
        return aLong == null ? 0 : aLong;
    }

    @Query("select pe from ProgramEncounter pe where pe.uuid =:id or pe.legacyId = :id")
    ProgramEncounter findByLegacyIdOrUuid(String id);

    @Query("select pe from ProgramEncounter pe where pe.legacyId = :id")
    ProgramEncounter findByLegacyId(String id);

    default Specification<ProgramEncounter> withProgramEncounterId(Long id) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
        {
            return id == null ? null : cb.equal(root.get("programEnrolment").get("id"), id);
        };
    }

    default Specification<ProgramEncounter> withProgramEncounterEarliestVisitDateTime(DateTime earliestVisitDateTime) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                earliestVisitDateTime == null ? null : cb.equal(root.get("earliestVisitDateTime").as(java.sql.Date.class), earliestVisitDateTime.toDate());
    }

    default Specification<ProgramEncounter> withProgramEncounterDateTime(DateTime encounterDateTime) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                encounterDateTime == null ? null : cb.equal(root.get("encounterDateTime").as(java.sql.Date.class), encounterDateTime.toDate());
    }

    default Specification<ProgramEncounter> withNotNullEncounterDateTime() {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> cb.isNotNull(root.get("encounterDateTime"));
    }

    default Specification<ProgramEncounter> withVoidedFalse() {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> cb.isFalse(root.get("isVoided"));
    }

    default Specification<ProgramEncounter> withNotNullCancelDateTime() {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> cb.isNotNull(root.get("cancelDateTime"));
    }

    default Specification<ProgramEncounter> withProgramEncounterTypeIdUuids(List<String> encounterTypeUuids) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                encounterTypeUuids.isEmpty() ? null : root.get("encounterType").get("uuid").in(encounterTypeUuids);
    }

    default Specification<ProgramEncounter> withEncounterType(EncounterType encounterType) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                encounterType == null ? null :
                        cb.and(cb.equal(root.<ProgramEncounter, EncounterType>join("encounterType"), encounterType));
    }

    default Specification<ProgramEncounter> withProgramEnrolment(ProgramEnrolment programEnrolment) {
        return (Root<ProgramEncounter> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                programEnrolment == null ? null : cb.and(cb.equal(root.<ProgramEncounter, ProgramEnrolment>join("programEnrolment"), programEnrolment));
    }

    default Page<ProgramEncounter> search(SearchParams searchParams, Pageable pageable) {
        return findAll(where(lastModifiedBetween(searchParams.lastModifiedDateTime, searchParams.now))
                .and(withConceptValues(searchParams.concepts, "observations"))
                .and(withEncounterType(searchParams.encounterType))
                .and(withProgramEnrolment(searchParams.programEnrolment)), pageable);
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update program_encounter enc set " +
            "address_id = :addressId, " +
            "sync_concept_1_value = :syncAttribute1Value, " +
            "sync_concept_2_value = :syncAttribute2Value, " +
            "last_modified_date_time = (current_timestamp + enc.id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "where enc.individual_id = :individualId", nativeQuery = true)
    void updateSyncAttributes(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value, Long lastModifiedById);

    default void updateSyncAttributes(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value) {
        this.updateSyncAttributes(individualId, addressId, syncAttribute1Value, syncAttribute2Value, UserContextHolder.getUserId());
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update program_encounter enc set " +
            "sync_concept_1_value = CAST((i.observations ->> CAST(:syncAttribute1 as text)) as text), " +
            "sync_concept_2_value = CAST((i.observations ->> CAST(:syncAttribute2 as text)) as text), " +
            "last_modified_date_time = (current_timestamp + enc.id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "from individual i " +
            "where enc.individual_id = i.id and i.subject_type_id = :subjectTypeId", nativeQuery = true)
    void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2, Long lastModifiedById);

    default void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2) {
        this.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncAttribute1, syncAttribute2, UserContextHolder.getUserId());
    }

    class SearchParams {
        public Date lastModifiedDateTime;
        public Date now;
        public Map<Concept, String> concepts;
        public EncounterType encounterType;
        public ProgramEnrolment programEnrolment;

        public SearchParams(Date lastModifiedDateTime, Date now, Map<Concept, String> conceptsMatchingValue, EncounterType encounterType, ProgramEnrolment programEnrolment) {
//            When the date is not specified the search should not limit results
            if (lastModifiedDateTime == null) {
                this.lastModifiedDateTime = DateTime.parse("2000-01-01").toDate();
                this.now = DateTime.now().toDate();
            } else {
                this.lastModifiedDateTime = lastModifiedDateTime;
                this.now = now;
            }

            this.concepts = conceptsMatchingValue;
            this.encounterType = encounterType;
            this.programEnrolment = programEnrolment;
        }
    }

    @Modifying
    @Query(value = "update program_encounter e set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById " +
            "from individual i, program_enrolment enrolment" +
            " where i.id = enrolment.individual_id and i.address_id = :addressId and enrolment.id = e.program_enrolment_id and e.is_voided = false", nativeQuery = true)
    void voidSubjectItemsAt(Long addressId, Long lastModifiedById);
    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectItemsAt(address.getId(), UserContextHolder.getUserId());
    }
}
