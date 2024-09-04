package org.avni.server.dao;

import org.avni.server.domain.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "groups", path = "groups")
public interface GroupRepository extends ReferenceDataRepository<Group>, FindByLastModifiedDateTime<Group> {

    Group findByNameAndOrganisationId(String name, Long organisationId);

    Group findByIdAndOrganisationId(Long groupId, Long organisationId);

    @RestResource(exported = false)
    Long deleteAllByNameNot(String name);

    List<Group> findAllByName(String name);

    List<Group> findByIdInAndIsVoidedFalse(Long[] ids);

    Page<Group> findByNameNotAndIsVoidedFalse(String name, Pageable pageable);

}
