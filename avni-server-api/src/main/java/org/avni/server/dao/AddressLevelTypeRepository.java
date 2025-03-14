package org.avni.server.dao;

import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.AddressLevelTypes;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "addressLevelType", path = "addressLevelType")
public interface AddressLevelTypeRepository extends ReferenceDataRepository<AddressLevelType> {

    AddressLevelType findByNameAndOrganisationId(String name, Long organisationId);
    AddressLevelType findByNameIgnoreCaseAndOrganisationIdAndIsVoidedFalse(String name, Long organisationId);

    @RestResource(path = "findAllById", rel = "findAllById")
    List<AddressLevelType> findByIdIn(@Param("ids") Long[] ids);

    List<AddressLevelType> findAllByIdIn(Collection<Long> id);

    @Query("select a.name from AddressLevelType a where a.isVoided = false")
    List<String> getAllNames();

    @Query(value = "select * from address_level_type where id not in (select distinct parent_id from address_level_type where parent_id is not null) and is_voided = false", nativeQuery = true)
    List<AddressLevelType> getAllLowestAddressLevelTypes();

    List<AddressLevelType> findByUuidIn(Collection<@NotNull String> uuid);

    AddressLevelType findByParent(AddressLevelType parent);

    List<AddressLevelType> findByIsVoidedFalseAndNameIgnoreCaseContains(String name);

    @Query(value = "WITH RECURSIVE parent_hierarchy AS (" +
            "    SELECT id, parent_id, name " +
            "    FROM address_level_type " +
            "    WHERE name = :name AND is_voided = false " +
            "    UNION ALL " +
            "    SELECT alt.id, alt.parent_id, alt.name " +
            "    FROM address_level_type alt " +
            "    INNER JOIN parent_hierarchy ph ON alt.id = ph.parent_id " +
            "    WHERE alt.is_voided = false" +
            ") " +
            "SELECT name FROM parent_hierarchy",
            nativeQuery = true)
    List<String> getAllParentNames(@Param("name") String name);

    default AddressLevelTypes getAllAddressLevelTypes() {
        return new AddressLevelTypes(this.findAllByIsVoidedFalse());
    }
}
