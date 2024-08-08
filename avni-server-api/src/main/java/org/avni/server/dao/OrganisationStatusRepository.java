package org.avni.server.dao;

import org.avni.server.domain.organisation.OrganisationStatus;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "organisationStatus", path = "organisationStatus")
public interface OrganisationStatusRepository extends AvniJpaRepository<OrganisationStatus, Long>, CHSRepository<OrganisationStatus> {
    @RestResource(path = "findAllById", rel = "findAllById")
    List<OrganisationStatus> findByIdIn(@Param("ids") Long[] ids);
}
