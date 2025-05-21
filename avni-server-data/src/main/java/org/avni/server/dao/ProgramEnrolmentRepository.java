package org.avni.server.dao;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.projection.SearchSubjectEnrolledProgram;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@Repository
@RepositoryRestResource(collectionResourceRel = "programEnrolment", path = "programEnrolment", exported = false)
public interface ProgramEnrolmentRepository extends TransactionalDataRepository<ProgramEnrolment>, FindByLastModifiedDateTime<ProgramEnrolment>, OperatingIndividualScopeAwareRepository<ProgramEnrolment>, SubjectTreeItemRepository {

    @Query("select new org.avni.server.projection.SearchSubjectEnrolledProgram(pe.individual.id, pe.program) from ProgramEnrolment pe join pe.program join pe.program.operationalPrograms where pe.individual.id in :individualIds and pe.programExitDateTime is null and pe.isVoided = false")
    List<SearchSubjectEnrolledProgram> findActiveEnrolmentsByIndividualIds(List<Long> individualIds);

    @Query("select enl from ProgramEnrolment enl " +
            "join enl.individual i " +
            "where enl.program.id = :programId " +
            "and enl.isVoided = false " +
            "and i.isVoided = false " +
            "and coalesce(enl.enrolmentDateTime, enl.programExitDateTime) between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds, null) is null OR i.addressLevel.id in :locationIds)")
    Stream<ProgramEnrolment> findNonVoidedEnrolments(Long programId, List<Long> locationIds, DateTime startDateTime, DateTime endDateTime);

    @Query("select enl from ProgramEnrolment enl " +
            "join enl.individual i " +
            "where enl.program.id = :programId " +
            "and coalesce(enl.enrolmentDateTime, enl.programExitDateTime) between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds, null) is null OR i.addressLevel.id in :locationIds)")
    Stream<ProgramEnrolment> findAllEnrolments(Long programId, List<Long> locationIds, DateTime startDateTime, DateTime endDateTime);

    //group by is added for distinct enl records
    @Query("select enl from ProgramEnrolment enl " +
            "join enl.programEncounters enc " +
            "join enl.individual i " +
            "where enc.encounterType.id = :encounterTypeId " +
            "and enl.program.id = :programId " +
            "and enc.isVoided = false " +
            "and enl.isVoided = false " +
            "and i.isVoided = false " +
            "and coalesce(enc.encounterDateTime, enc.cancelDateTime) between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds, null) is null OR i.addressLevel.id in :locationIds) " +
            "group by enl.id")
    Stream<ProgramEnrolment> findNonVoidedProgramEncounters(List<Long> locationIds, DateTime startDateTime, DateTime endDateTime, Long encounterTypeId, Long programId);

    @Query("select enl from ProgramEnrolment enl " +
            "join enl.programEncounters enc " +
            "join enl.individual i " +
            "where enc.encounterType.id = :encounterTypeId " +
            "and enl.program.id = :programId " +
            "and coalesce(enc.encounterDateTime, enc.cancelDateTime) between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds, null) is null OR i.addressLevel.id in :locationIds) " +
            "group by enl.id")
    Stream<ProgramEnrolment> findAllProgramEncounters(List<Long> locationIds, DateTime startDateTime, DateTime endDateTime, Long encounterTypeId, Long programId);

    Page<ProgramEnrolment> findByLastModifiedDateTimeGreaterThanAndLastModifiedDateTimeLessThanAndProgramNameOrderByLastModifiedDateTimeAscIdAsc(
            Date lastModifiedDateTime,
            Date now,
            String program,
            Pageable pageable);

    Page<ProgramEnrolment> findByProgramNameAndIndividualUuidOrderByLastModifiedDateTimeAscIdAsc(
            String program,
            String individualUuid,
            Pageable pageable);

    @Query("select pe from ProgramEnrolment pe where pe.uuid =:id or pe.legacyId = :id")
    ProgramEnrolment findByLegacyIdOrUuid(String id);

    @Query("select pe from ProgramEnrolment pe where pe.legacyId = :id")
    ProgramEnrolment findByLegacyId(String id);

    Page<ProgramEnrolment> findByLastModifiedDateTimeGreaterThanAndLastModifiedDateTimeLessThanOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);

    @Override
    default Specification<ProgramEnrolment> syncTypeIdSpecification(Long typeId) {
        return (Root<ProgramEnrolment> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("program").get("id"), typeId);
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return count(syncEntityChangedAuditSpecification(syncParameters)
                .and(syncTypeIdSpecification(syncParameters.getTypeId()))
                .and(syncStrategySpecification(syncParameters))
        ) > 0;
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update program_enrolment enl set " +
            "address_id = :addressId, " +
            "sync_concept_1_value = :syncAttribute1Value, " +
            "sync_concept_2_value = :syncAttribute2Value, " +
            "last_modified_date_time = (current_timestamp + enl.id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "where enl.individual_id = :individualId", nativeQuery = true)
    void updateSyncAttributesForIndividual(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value, Long lastModifiedById);

    default void updateSyncAttributesForIndividual(Long individualId, Long addressId, String syncAttribute1Value, String syncAttribute2Value) {
        this.updateSyncAttributesForIndividual(individualId, addressId, syncAttribute1Value, syncAttribute2Value, UserContextHolder.getUserId());
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update program_enrolment enl set " +
            "sync_concept_1_value = CAST((i.observations ->> CAST(:syncAttribute1 as text)) as text), " +
            "sync_concept_2_value = CAST((i.observations ->> CAST(:syncAttribute2 as text)) as text), " +
            "last_modified_date_time = (current_timestamp + id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "from individual i " +
            "where enl.individual_id = i.id and i.subject_type_id = :subjectTypeId", nativeQuery = true)
    void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2, Long lastModifiedById);

    default void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2) {
        this.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncAttribute1, syncAttribute2, UserContextHolder.getUserId());
    }

    @Modifying
    @Query(value = "update program_enrolment e set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById " +
            "from individual i" +
            " where i.address_id = :addressId and i.id = e.individual_id and e.is_voided = false", nativeQuery = true)
    void voidSubjectItemsAt(Long addressId, Long lastModifiedById);

    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectItemsAt(address.getId(), UserContextHolder.getUserId());
    }

    List<ProgramEnrolment> findByIndividualAndProgram(Individual individual, Program program);
}
