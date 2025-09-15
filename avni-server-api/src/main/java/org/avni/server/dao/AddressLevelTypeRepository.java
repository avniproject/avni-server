package org.avni.server.dao;

import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.AddressLevelTypes;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.*;

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

    AddressLevelType findByNameIgnoreCaseAndIsVoidedFalse(String name);

    default List<String> getAllParentNames(String uuid) {
        List<AddressLevelType> allAddressLevelTypes = getAllAddressLevelTypes();
        List<String> parentNames = new ArrayList<>();
        AddressLevelType current = allAddressLevelTypes.stream()
                .filter(alt -> alt.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);

        parentNames.add(current.getName());
        while (current != null && current.getParent() != null) {
            current = current.getParent();
            if (current != null) {
                parentNames.add(current.getName());
            }
        }
        return parentNames;
    }

    default AddressLevelTypes getAllAddressLevelTypes() {
        return new AddressLevelTypes(this.findAllByIsVoidedFalse());
    }
}
