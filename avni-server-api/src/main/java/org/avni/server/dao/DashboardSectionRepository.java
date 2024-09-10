package org.avni.server.dao;

import org.avni.server.domain.DashboardSection;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(collectionResourceRel = "dashboardSection", path = "dashboardSection")
public interface DashboardSectionRepository extends ReferenceDataRepository<DashboardSection>, JpaSpecificationExecutor<DashboardSection>, EndOfLife1EndpointRepository<DashboardSection> {
}
