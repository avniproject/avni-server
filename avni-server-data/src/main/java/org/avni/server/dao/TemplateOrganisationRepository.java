package org.avni.server.dao;

import org.avni.server.domain.TemplateOrganisation;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "templateOrganisation", path = "templateOrganisation")
public interface TemplateOrganisationRepository extends ReferenceDataRepository<TemplateOrganisation> {
    TemplateOrganisation findByUuid(String uuid);
    TemplateOrganisation findByOrganisationId(Long organisationId);
    List<TemplateOrganisation> findByActiveTrue();
}
