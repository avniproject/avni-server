package org.avni.server.dao;

import org.avni.server.domain.DashboardSection;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
@RepositoryRestResource(collectionResourceRel = "dashboardSection", path = "dashboardSection")
public interface DashboardSectionRepository extends ReferenceDataRepository<DashboardSection>, JpaSpecificationExecutor<DashboardSection> {
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<DashboardSection> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);
}
