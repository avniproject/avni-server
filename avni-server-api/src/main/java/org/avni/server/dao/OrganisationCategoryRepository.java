package org.avni.server.dao;

import org.avni.server.domain.organisation.OrganisationCategory;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "organisationCategory", path = "organisationCategory")
public interface OrganisationCategoryRepository extends AvniJpaRepository<OrganisationCategory, Long>, CHSRepository<OrganisationCategory> {
    @RestResource(path = "findAllById", rel = "findAllById")
    List<OrganisationCategory> findByIdIn(@Param("ids") Long[] ids);
}
