package org.avni.server.dao;

import jakarta.persistence.criteria.*;
import org.avni.server.application.projections.WebSearchResultProjection;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.projection.IndividualWebProjection;
import org.avni.server.util.S;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@RepositoryRestResource(collectionResourceRel = "individual", path = "individual", exported = false)
public interface IndividualRepository extends TransactionalDataRepository<Individual>, OperatingIndividualScopeAwareRepository<Individual>, SubjectTreeItemRepository {
    @Override
    default Specification<Individual> syncTypeIdSpecification(Long typeId) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                cb.equal(root.get("subjectType").get("id"), typeId);
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        Specification<Individual> audit = syncEntityChangedAuditSpecification(syncParameters);
        Specification<Individual> subjectType = syncTypeIdSpecification(syncParameters.getTypeId());
        Specification<Individual> location_AndDirectAssignment_AndSyncAttributes = syncStrategySpecification(syncParameters);
        return count(audit
                .and(subjectType)
                .and(location_AndDirectAssignment_AndSyncAttributes)
        ) > 0;
    }

    default Specification<Individual> getFilterSpecForVoid(Boolean includeVoided) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                includeVoided == null || includeVoided ? cb.and() : cb.isFalse(root.get("isVoided"));
    }

    default Page<Individual> findByName(String name, Pageable pageable) {
        return findAll(getFilterSpecForName(name), pageable);
    }

    Page<Individual> findByIdIn(Long[] ids, Pageable pageable);

    default Specification<Individual> getFilterSpecForName(String value) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (value != null && !value.isEmpty()) {
                Predicate[] predicates = new Predicate[2];
                String[] values = value.trim().split(" ");
                if (values.length > 0) {
                    predicates[0] = cb.like(cb.upper(root.get("firstName")), values[0].toUpperCase() + "%");
                    predicates[1] = cb.like(cb.upper(root.get("lastName")), values[0].toUpperCase() + "%");
                }
                if (values.length > 1) {
                    predicates[1] = cb.like(cb.upper(root.get("lastName")), values[1].toUpperCase() + "%");
                    return cb.and(predicates[0], predicates[1]);
                }
                return cb.or(predicates[0], predicates[1]);
            }
            return cb.and();
        };
    }

    default Specification<Individual> getFilterSpecForSubjectTypeId(String subjectTypeUUID) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                subjectTypeUUID == null ? cb.and() : root.get("subjectType").get("uuid").in(subjectTypeUUID);
    }

    default Specification<Individual> getFilterSpecForObs(String value) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                value == null ? cb.and() : cb.or(
                        jsonContains(root.get("observations"), "%" + value + "%", cb),
                        jsonContains(root.join("programEnrolments", JoinType.LEFT).get("observations"), "%" + value + "%", cb));
    }

    default Optional<Individual> findByConceptWithMatchingPattern(Concept concept, String pattern) {
        Specification<Individual> specification = (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
            cb.like(jsonExtractPathText(root.get("observations"), concept.getUuid(), cb), pattern);

        List<Individual> individuals = findAll(specification);
        return individuals.isEmpty() ? Optional.empty() : Optional.of(individuals.get(0));
    }

        default Specification<Individual> getFilterSpecForLocationIds(List<Long> locationIds) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                locationIds == null ? cb.and() : root.get("addressLevel").get("id").in(locationIds);
    }

    default Specification<Individual> getFilterSpecForAddress(String locationName) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                locationName == null ? cb.and() :
                        cb.like(cb.upper(root.get("addressLevel").get("titleLineage")), "%" + locationName.toUpperCase() + "%");
    }

    @Query("select ind from Individual ind " +
            "where ind.isVoided = false " +
            "and ind.subjectType.id = :subjectTypeId " +
            "and ind.registrationDate between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds,NULL) is null OR ind.addressLevel.id in :locationIds)")
    Stream<Individual> findNonVoidedIndividuals(Long subjectTypeId, List<Long> locationIds, LocalDate startDateTime, LocalDate endDateTime);

    @Query("select ind from Individual ind " +
            "where ind.subjectType.id = :subjectTypeId " +
            "and ind.registrationDate between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds,NULL) is null OR ind.addressLevel.id in :locationIds)")
    Stream<Individual> findAllIndividuals(Long subjectTypeId, List<Long> locationIds, LocalDate startDateTime, LocalDate endDateTime);

    //group by is added for distinct ind records
    @Query("select i from Individual i " +
            "join i.encounters enc " +
            "where enc.encounterType.id = :encounterTypeId " +
            "and enc.isVoided = false " +
            "and i.isVoided = false " +
            "and coalesce(enc.encounterDateTime, enc.cancelDateTime) between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds, null) is null OR i.addressLevel.id in :locationIds)" +
            "group by i.id")
    Stream<Individual> findNonVoidedEncounters(List<Long> locationIds, DateTime startDateTime, DateTime endDateTime, Long encounterTypeId);

    @Query("select i from Individual i " +
            "join i.encounters enc " +
            "where enc.encounterType.id = :encounterTypeId " +
            "and coalesce(enc.encounterDateTime, enc.cancelDateTime) between :startDateTime and :endDateTime " +
            "and (coalesce(:locationIds, null) is null OR i.addressLevel.id in :locationIds)" +
            "group by i.id")
    Stream<Individual> findAllEncounters(List<Long> locationIds, DateTime startDateTime, DateTime endDateTime, Long encounterTypeId);


    @Query("select i from Individual i where i.uuid =:id or i.legacyId = :id")
    Individual findByLegacyIdOrUuid(String id);

    @Query("select i from Individual i where i.legacyId = :id")
    Individual findByLegacyId(String id);

    @Query("select i from Individual i where (i.uuid =:id or i.legacyId = :id) and i.subjectType = :subjectType")
    Individual findByLegacyIdOrUuidAndSubjectType(String id, SubjectType subjectType);

    @Query(value = "select firstname,lastname,fullname,id,uuid,title_lineage,subject_type_name,gender_name,date_of_birth,enrolments,total_elements from web_search_function(:jsonSearch, :dbUser)", nativeQuery = true)
    List<WebSearchResultProjection> getWebSearchResults(String jsonSearch, String dbUser);

    default Specification<Individual> findBySubjectTypeSpec(String subjectTypeName) {
        Specification<Individual> spec = (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Join<Individual, SubjectType> subjectTypeJoin = root.join("subjectType", JoinType.LEFT);
            return cb.and(cb.equal(subjectTypeJoin.get("name"), subjectTypeName));
        };
        return spec;
    }

    default Specification<Individual> findInLocationSpec(List<Long> addressIds) {
        return (Root<Individual> root, CriteriaQuery<?> query, CriteriaBuilder cb) ->
                addressIds.isEmpty() ? null : root.get("addressLevel").get("id").in(addressIds);
    }

    default Page<Individual> findSubjects(IndividualSearchParams individualSearchParams, Pageable pageable) {
        Specification specification = withConceptValues(individualSearchParams.getObservations(), "observations");
        if (individualSearchParams.getLastModifiedDateTime() != null)
            specification = specification.and(lastModifiedBetween(CHSEntity.toDate(individualSearchParams.getLastModifiedDateTime()), CHSEntity.toDate(individualSearchParams.getNow())));
        if (!individualSearchParams.getAllLocationIds().isEmpty())
            specification = specification.and(findInLocationSpec(individualSearchParams.getAllLocationIds()));
        if (!S.isEmpty((individualSearchParams.getSubjectTypeName())))
            specification = specification.and(findBySubjectTypeSpec(individualSearchParams.getSubjectTypeName()));
        return findAll(specification, pageable);
    }

    List<Individual> findAllByAddressLevelAndSubjectTypeAndIsVoidedFalse(AddressLevel addressLevel, SubjectType subjectType);
    List<Individual> findAllByAddressLevelAndSubjectType(AddressLevel addressLevel, SubjectType subjectType);

    List<IndividualWebProjection> findAllIndividualWebProjectionByUuidIn(List<String> uuids);

    @Modifying(clearAutomatically = true)
    @Query(value = "update individual i set " +
            "sync_concept_1_value = CAST((i.observations ->> CAST(:syncAttribute1 as text)) as text), " +
            "sync_concept_2_value = CAST((i.observations ->> CAST(:syncAttribute2 as text)) as text), " +
            "last_modified_date_time = (current_timestamp + i.id * (interval '1 millisecond')/1000), last_modified_by_id = :lastModifiedById " +
            "where i.subject_type_id = :subjectTypeId", nativeQuery = true)
    void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2, Long lastModifiedById);

    default void updateConceptSyncAttributesForSubjectType(Long subjectTypeId, String syncAttribute1, String syncAttribute2) {
        this.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncAttribute1, syncAttribute2, UserContextHolder.getUserId());
    }

    boolean existsByAddressLevelIdIn(List<Long> addressIds);
    default boolean hasSubjectsInLocations(List<Long> addressIds) {
        return addressIds.isEmpty() ? false : existsByAddressLevelIdIn(addressIds);
    }
    boolean existsBySubjectTypeUuid(String subjectTypeUUID);
    boolean existsBySubjectTypeId(Long subjectTypeId);

    default Individual getSubject(String uuid, String legacyId) {
        Individual individual = null;
        if (StringUtils.hasLength(uuid)) {
            individual = this.findByUuid(uuid);
        }
        if (individual == null && StringUtils.hasLength(legacyId)) {
            individual = this.findByLegacyId(legacyId.trim());
        }
        return individual;
    }

    @Modifying
    @Query(value = "update individual i set is_voided = true, last_modified_date_time = (current_timestamp + random() * 5000 * (interval '1 millisecond')), last_modified_by_id = :lastModifiedById where i.address_id = :addressId and i.is_voided = false", nativeQuery = true)
    void voidSubjectItemsAt(Long addressId, Long lastModifiedById);
    default void voidSubjectItemsAt(AddressLevel address) {
        this.voidSubjectItemsAt(address.getId(), UserContextHolder.getUserId());
    }

    int countBySyncDisabled(boolean syncDisabled);
}
