package org.avni.server.dao;

import org.avni.server.domain.AbhaLink;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Not exported over Spring Data REST: every row carries a patient's ABHA number.
@Repository
@RepositoryRestResource(exported = false)
public interface AbhaLinkRepository extends TransactionalDataRepository<AbhaLink> {
    // Both served by abha_link_org_number_live, a partial index on (organisation_id, abha_number) for live rows, then
    // the individual's primary key. A subject voided in bulk (a location's subjects, by SQL) keeps a live row; the join
    // keeps it out of the answer.
    // Every live holder in the organisation, whatever their subject type: for the warning when a number is held twice.
    String LIVE_SUBJECT_UUIDS = "select i.uuid from public.abha_link l join public.individual i on i.id = l.individual_id " +
            "where l.organisation_id = :organisationId and l.abha_number = :abhaNumber " +
            "and l.is_voided = false and i.is_voided = false order by i.uuid";
    // The live holders of one subject type: the search's answer, since its privilege is checked on that type.
    String LIVE_SUBJECT_UUIDS_OF_TYPE = "select i.uuid from public.abha_link l join public.individual i on i.id = l.individual_id " +
            "where l.organisation_id = :organisationId and l.abha_number = :abhaNumber " +
            "and l.is_voided = false and i.is_voided = false and i.subject_type_id = :subjectTypeId order by i.uuid";

    // By ABHA address: the part before '@', in any case (abha_link_org_address_live, V1_415).
    String LIVE_SUBJECT_UUIDS_BY_ADDRESS = "select i.uuid from public.abha_link l join public.individual i on i.id = l.individual_id " +
            "where l.organisation_id = :organisationId and split_part(lower(l.abha_address), '@', 1) = split_part(lower(:abhaAddress), '@', 1) " +
            "and l.is_voided = false and i.is_voided = false order by i.uuid";
    String LIVE_SUBJECT_UUIDS_OF_TYPE_BY_ADDRESS = "select i.uuid from public.abha_link l join public.individual i on i.id = l.individual_id " +
            "where l.organisation_id = :organisationId and split_part(lower(l.abha_address), '@', 1) = split_part(lower(:abhaAddress), '@', 1) " +
            "and l.is_voided = false and i.is_voided = false and i.subject_type_id = :subjectTypeId order by i.uuid";

    Optional<AbhaLink> findByIndividualIdAndIsVoidedFalse(Long individualId);

    @Query(value = LIVE_SUBJECT_UUIDS_BY_ADDRESS, nativeQuery = true)
    List<String> findLiveSubjectUUIDsByAddress(@Param("organisationId") Long organisationId, @Param("abhaAddress") String abhaAddress);

    @Query(value = LIVE_SUBJECT_UUIDS_OF_TYPE_BY_ADDRESS, nativeQuery = true)
    List<String> findLiveSubjectUUIDsOfTypeByAddress(@Param("organisationId") Long organisationId, @Param("abhaAddress") String abhaAddress,
                                                     @Param("subjectTypeId") Long subjectTypeId);

    @Query(value = LIVE_SUBJECT_UUIDS, nativeQuery = true)
    List<String> findLiveSubjectUUIDs(@Param("organisationId") Long organisationId, @Param("abhaNumber") String abhaNumber);

    @Query(value = LIVE_SUBJECT_UUIDS_OF_TYPE, nativeQuery = true)
    List<String> findLiveSubjectUUIDsOfType(@Param("organisationId") Long organisationId, @Param("abhaNumber") String abhaNumber,
                                            @Param("subjectTypeId") Long subjectTypeId);
}
