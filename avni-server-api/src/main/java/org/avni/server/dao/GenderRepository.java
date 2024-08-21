package org.avni.server.dao;

import org.avni.server.domain.Gender;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(collectionResourceRel = "gender", path = "gender")
public interface GenderRepository extends CHSRepository<Gender>, CustomCHSJpaRepository<Gender, Long>, FindByLastModifiedDateTime<Gender> {
    Gender findByName(String name);
    Gender findByNameAndOrganisationId(String name, Long organisationId);
    Gender findByNameIgnoreCase(String name);

    @RestResource(exported = false)
    Gender save(Gender gender);

    default Gender findOne(long id) {
        return findById(id).orElse(null);
    };

    default Gender findByUuidOrName(String name, String uuid) {
        return uuid != null ? findByUuid(uuid) : findByName(name);
    }
}
