package org.avni.server.dao;

import jakarta.persistence.QueryHint;
import jakarta.validation.constraints.NotNull;
import org.avni.server.application.projections.CatchmentAddressProjection;
import org.avni.server.application.projections.LocationProjection;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.Catchment;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.DateTimeUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RepositoryRestResource(collectionResourceRel = "locations", path = "locations")
public interface LocationRepository extends ReferenceDataRepository<AddressLevel>, FindByLastModifiedDateTime<AddressLevel>, OperatingIndividualScopeAwareRepository<AddressLevel> {

    @Query(value = "select al.id, al.uuid, title, type_id as typeId, alt.name as typeString, al.parent_id as parentId, " +
            "cast(lineage as text) as lineage, alt.level " +
            "from address_level al " +
            "left join address_level_type alt on alt.id = al.type_id " +
            "where al.id in (:ids)",
            nativeQuery = true)
    List<LocationProjection> findByIdIn(Long[] ids);

    @Query(value = "select al1.*\n" +
            "from catchment c\n" +
            "         inner join catchment_address_mapping cam on c.id = cam.catchment_id\n" +
            "         inner join address_level al on cam.addresslevel_id = al.id\n" +
            "         inner join address_level al1 on al.lineage @> al1.lineage \n" +
            "where c.id = :catchmentId\n" +
            "  and c.organisation_id = :organisationId\n" +
            "  and al1.last_modified_date_time between :lastModifiedDateTime and :now\n" +
            "order by al1.last_modified_date_time asc, al1.id asc", nativeQuery = true)
    Page<AddressLevel> getSyncResults(long catchmentId, Instant lastModifiedDateTime, Instant now, long organisationId, Pageable pageable);

    @Query(value = "select count(*)\n" +
            "from catchment c\n" +
            "         inner join catchment_address_mapping cam on c.id = cam.catchment_id\n" +
            "         inner join address_level al on cam.addresslevel_id = al.id\n" +
            "         inner join address_level al1 on al.lineage @> al1.lineage \n" +
            "where c.id = :catchmentId\n" +
            "  and al1.last_modified_date_time > :lastModifiedDateTime\n", nativeQuery = true)
    Long getChangedRowCount(long catchmentId, Instant lastModifiedDateTime);


    AddressLevel findByTitleAndCatchmentsUuid(String title, String uuid);

    String locationProjectionBaseQuery = "select al.id, al.uuid, al.title, al.type_id as typeId, alt.name as typeString, al.parent_id as parentId, " +
            "cast(al.lineage as text) as lineage, alt.level " +
            "from address_level al " +
            "left join address_level_type alt on alt.id = al.type_id " +
            "where (:title is null or lower(al.title) like lower(concat('%', :title,'%'))) " +
            "and al.is_voided = false ";

    String orderByTitle = " order by al.title ";
    String findLocationProjectionByTitleQuery = locationProjectionBaseQuery + orderByTitle;

    @Query(value = findLocationProjectionByTitleQuery,
            nativeQuery = true)
    Page<LocationProjection> findLocationProjectionByTitleIgnoreCase(String title, Pageable pageable);

    String addressLevelTypeClause = " and alt.id = :typeId ";
    String findLocationProjectionByTitleIgnoreCaseAndTypeIdQuery = locationProjectionBaseQuery + addressLevelTypeClause + orderByTitle;

    @Query(value = findLocationProjectionByTitleIgnoreCaseAndTypeIdQuery,
            nativeQuery = true)
    Page<LocationProjection> findLocationProjectionByTitleIgnoreCaseAndTypeId(String title, Integer typeId, Pageable pageable);

    @Query(value = findLocationProjectionByTitleIgnoreCaseAndTypeIdQuery,
            nativeQuery = true)
    List<LocationProjection> findLocationProjectionByTitleIgnoreCaseAndTypeIdAsList(String title, Integer typeId);

    String addressLevelParentClause = " and al.parent_id = :parentId ";
    String findLocationProjectionByTitleIgnoreCaseAndTypeIdAndParentIdQuery = locationProjectionBaseQuery + addressLevelTypeClause + addressLevelParentClause + orderByTitle;

    @Query(value = findLocationProjectionByTitleIgnoreCaseAndTypeIdAndParentIdQuery,
            nativeQuery = true)
    Page<LocationProjection> findLocationProjectionByTitleIgnoreCaseAndTypeIdAndParentId(String title, int typeId, Integer parentId, Pageable pageable);

    AddressLevel findByTitleIgnoreCase(String title);

    AddressLevel findByTitleIgnoreCaseAndTypeAndParentIsNull(String title, AddressLevelType addressLevelType);

    AddressLevel findByTitleIgnoreCaseAndTypeIn(String title, Collection<AddressLevelType> type);

    List<AddressLevel> findByCatchments(Catchment catchment);

    Page<AddressLevel> findByLastModifiedDateTimeAfterAndTypeIn(Instant lastModifiedDateTime, Collection<@NotNull AddressLevelType> type, Pageable pageable);

    boolean existsByLastModifiedDateTimeAfterAndTypeIn(Instant lastModifiedDateTime, Collection<@NotNull AddressLevelType> type);

    @Override
    default Page<AddressLevel> getSyncResults(SyncParameters syncParameters) {
        Long organisationId = UserContextHolder.getOrganisation().getId();
        return getSyncResults(syncParameters.getCatchment().getId(), DateTimeUtil.toInstant(syncParameters.getLastModifiedDateTime()),
                DateTimeUtil.toInstant(syncParameters.getNow()), organisationId, syncParameters.getPageable());
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return getChangedRowCount(syncParameters.getCatchment().getId(), DateTimeUtil.toInstant(syncParameters.getLastModifiedDateTime())) > 0;
    }

    default AddressLevel findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in Location. Field 'title' not unique.");
    }

    default AddressLevel findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in Location. Field 'title' not unique.");
    }

    @Query(value = "SELECT * FROM address_level WHERE lineage ~ CAST(:lquery as lquery) \n-- #pageable\n",
            countQuery = "SELECT count(*) FROM address_level WHERE lineage ~ CAST(:lquery as lquery)",
            nativeQuery = true)
    Page<AddressLevel> getAddressLevelsByLquery(@Param("lquery") String lquery, Pageable pageable);

    AddressLevel findByParentAndTitleIgnoreCaseAndIsVoidedFalse(AddressLevel parent, String title);

    default AddressLevel findChildLocation(AddressLevel parent, String title) {
        return this.findByParentAndTitleIgnoreCaseAndIsVoidedFalse(parent, title);
    }

    @Query("select a from AddressLevel a where (a.uuid =?1 or a.legacyId = ?1) and a.organisationId = ?2")
    @QueryHints({@QueryHint(name = org.hibernate.jpa.QueryHints.HINT_CACHEABLE, value = "true")})
    AddressLevel findByLegacyIdOrUuidAndOrgId(String id, Long organisationId);

    default AddressLevel findByLegacyIdOrUuid(String id) {
        return this.findByLegacyIdOrUuidAndOrgId(id, UserContextHolder.getUserContext().getOrganisationId());
    }

    @Query(value = "select al.*\n" +
            "from address_level al\n" +
            "         join address_level_type alt on al.type_id = alt.id\n" +
            "         left join address_level parent on parent.id = al.parent_id\n" +
            "where al.title = cast(:title as text)\n" +
            "and alt.name = cast(:type as text)\n" +
            "and case when :parentName isnull then true else parent.title = cast(:parentName as text) end;", nativeQuery = true)
    AddressLevel findLocationByTitleTypeAndParentName(String title, String type, String parentName);

    default AddressLevel findChildLocation(String title, String type, String parentName) {
        return this.findLocationByTitleTypeAndParentName(title, type, parentName);
    }

    List<AddressLevel> findByTitleIgnoreCaseAndTypeNameIgnoreCaseAndIsVoidedFalse(String title, String type);

    default List<AddressLevel> findLocation(String title, String type) {
        return this.findByTitleIgnoreCaseAndTypeNameIgnoreCaseAndIsVoidedFalse(title, type);
    }

    @RestResource(path = "findByParent", rel = "findByParent")
    Page<AddressLevel> findByIsVoidedFalseAndParent_Id(@Param("parentId") Long parentId, Pageable pageable);

    @RestResource(path = "autocompleteLocationsOfType", rel = "autocompleteLocationsOfType")
    List<AddressLevel> findByType_IdAndTitleIgnoreCaseStartingWithAndIsVoidedFalseOrderByTitleAsc(@Param("typeId") Long typeId,
                                                                                                  @Param("title") String title);

    @Query("select a.title from AddressLevel a where a.isVoided = false")
    List<String> getAllNames();

    @Query(value = "select lowestpoint_id from title_lineage_locations_view where lower(title_lineage) = lower(:locationTitleLineage)", nativeQuery = true)
    Long getAddressIdByLineage(String locationTitleLineage);

    default Optional<AddressLevel> findByTitleLineageIgnoreCase(String locationTitleLineage) {
        if (!StringUtils.hasText(locationTitleLineage)) {
            return Optional.empty();
        }
        Long addressId = getAddressIdByLineage(locationTitleLineage);
        return addressId != null ? findById(addressId) : Optional.empty();
    }

    List<AddressLevel> getAllByIsVoidedFalse();

    List<AddressLevel> findAllByParent(AddressLevel parent);

    List<AddressLevel> findByUuidIn(List<String> addressLevelUUIDs);

    List<AddressLevel> findByIsVoidedFalseAndTitleIgnoreCaseContains(String title);

    @Query(value = "select id from address_level where lineage ~ cast(:lquery as lquery)", nativeQuery = true)
    List<Long> getAllChildrenLocationsIds(@Param("lquery") String lquery); //actually returns List<Integer>

    @Query(value = "select * from address_level where lineage ~ cast(:lquery as lquery)", nativeQuery = true)
    List<AddressLevel> getAllChildLocations(@Param("lquery") String lquery);

    String CATCHMENT_ADDRESS_MAPPING_BASE_QUERY = "select row_number() over () as id, c.id as catchment_id, al1.id as addresslevel_id, al1.type_id as type_id from catchment c\n" +
            "                         inner join catchment_address_mapping cam on c.id = cam.catchment_id\n" +
            "                         inner join address_level al on cam.addresslevel_id = al.id\n" +
            "                         inner join address_level al1 on al.lineage @> al1.lineage\n";
    String CATCHMENT_ADDRESS_MAPPING_GROUP_BY = " group by 2, 3";
    @Query(value = CATCHMENT_ADDRESS_MAPPING_BASE_QUERY + "where c.id = :catchmentId" + CATCHMENT_ADDRESS_MAPPING_GROUP_BY, nativeQuery = true)
    List<CatchmentAddressProjection> getCatchmentAddressesForCatchmentId(@Param("catchmentId") Long catchmentId);

    @Query(value = CATCHMENT_ADDRESS_MAPPING_BASE_QUERY + "where c.id = :catchmentId and al1.type_id in (:typeIds)" + CATCHMENT_ADDRESS_MAPPING_GROUP_BY, nativeQuery = true)
    List<CatchmentAddressProjection> getCatchmentAddressesForCatchmentIdAndLocationTypeId(@Param("catchmentId") Long catchmentId, List<Long> typeIds);

    @Query(value = CATCHMENT_ADDRESS_MAPPING_BASE_QUERY + "where al1.id in (:addressLevelIds)" + CATCHMENT_ADDRESS_MAPPING_GROUP_BY, nativeQuery = true)
    List<CatchmentAddressProjection> getCatchmentAddressesForAddressLevelIds(@Param("addressLevelIds") List<Long> addressLevelIds);

    @Query(value = CATCHMENT_ADDRESS_MAPPING_BASE_QUERY + "where c.id = :catchmentId and al1.id = :addressLevelId" + CATCHMENT_ADDRESS_MAPPING_GROUP_BY, nativeQuery = true)
    List<CatchmentAddressProjection> getCatchmentAddressForCatchmentIdAndAddressLevelId(@Param("addressLevelId") Long addressLevelId, @Param("catchmentId") Long catchmentId);

    @Query(value = "select title_lineage from title_lineage_locations_function(:addressId)", nativeQuery = true)
    String getTitleLineageById(Long addressId);

    @Query(value = "select al.id, al.uuid, title, type_id as typeId, alt.name as typeString, al.parent_id as parentId, " +
            "cast(lineage as text) as lineage, " +
            "alt.level " +
            "from address_level al " +
            "left join address_level_type alt on alt.id = al.type_id " +
            "where al.is_voided = false",
            nativeQuery = true)
    Page<LocationProjection> findNonVoidedLocations(Pageable pageable);

    @Query(value = "select al.id, al.uuid, title, type_id as typeId, alt.name as typeString, al.parent_id as parentId, " +
            "cast(lineage as text) as lineage, alt.level " +
            "from address_level al " +
            "left join address_level_type alt on alt.id = al.type_id " +
            "where al.is_voided = false " +
            "and al.type_id = :typeId " +
            "order by al.title ",
            nativeQuery = true)
    List<LocationProjection> findNonVoidedLocationsByTypeId(Long typeId);

    @Query(value = "select al.id, al.uuid, title, type_id as typeId, alt.name as typeString, al.parent_id as parentId, " +
            "cast(lineage as text) as lineage, alt.level " +
            "from address_level al " +
            "left join address_level_type alt on alt.id = al.type_id " +
            "where al.is_voided = false " +
            "and al.uuid = :uuid ",
            nativeQuery = true)
    LocationProjection findNonVoidedLocationsByUuid(String uuid);

    @Query(value = "select al.id, al.uuid, title, type_id as typeId, alt.name as typeString, al.parent_id as parentId,\n" +
            "cast(lineage as text) as lineage, alt.level " +
            "from address_level al\n" +
            "left join address_level_type alt on alt.id = al.type_id\n" +
            "where lineage @>" +
            "          (select lineage" +
            "          from address_level" +
            "          where uuid = :uuid) and alt.parent_id <= :maxLevelTypeId",
            nativeQuery = true)
    List<LocationProjection> getParentsWithMaxLevelTypeId(String uuid, Long maxLevelTypeId);

    @Query(value = "select al.id, al.uuid, title, type_id as typeId, alt.name as typeString, al.parent_id as parentId,\n" +
            "cast(lineage as text) as lineage, alt.level " +
            "from address_level al\n" +
            "left join address_level_type alt on alt.id = al.type_id\n" +
            "where lineage @>" +
            "          (select lineage" +
            "          from address_level" +
            "         where uuid = :uuid)",
            nativeQuery = true)
    List<LocationProjection> getParents(String uuid);

    List<AddressLevel> findByTitleAndType(String title, AddressLevelType lowestAddressLevelType, Pageable pageable);

    AddressLevel findByTitleAndTypeAndIsVoidedFalse(String title, AddressLevelType addressLevelType);

    List<AddressLevel> findAllByIdIn(List<Long> addressIds);
}
