package org.avni.server.dao;

import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(collectionResourceRel = "dashboardFilter", path = "dashboardFilter")
@PreAuthorize("hasAnyAuthority('user','admin')")
public interface DashboardFilterRepository extends ReferenceDataRepository<DashboardFilter>, FindByLastModifiedDateTime<DashboardFilter>, JpaSpecificationExecutor<DashboardFilter> {

}
