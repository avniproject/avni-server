package org.avni.server.dao;

import org.avni.server.domain.Dashboard;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "dashboard", path = "dashboard")
public interface DashboardRepository extends ReferenceDataRepository<Dashboard>, JpaSpecificationExecutor<Dashboard> {

    @Query("select d.name from Dashboard d where d.isVoided = false")
    List<String> getAllNames();

    List<Dashboard> findAllByIsVoidedFalseOrderByName();

    Dashboard findByUuidAndOrganisationIdAndIsVoidedFalse(String uuid, Long organisationId);

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<Dashboard> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);
}
