package org.avni.server.dao;

import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(collectionResourceRel = "dashboardFilter", path = "dashboardFilter")
public interface DashboardFilterRepository extends ReferenceDataRepository<DashboardFilter>, JpaSpecificationExecutor<DashboardFilter>, EndOfLife1EndpointRepository<DashboardFilter> {
}
