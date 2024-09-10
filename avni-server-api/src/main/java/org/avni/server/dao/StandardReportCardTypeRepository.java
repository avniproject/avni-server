package org.avni.server.dao;

import org.avni.server.domain.StandardReportCardType;
import org.avni.server.domain.StandardReportCardTypeType;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
@RepositoryRestResource(collectionResourceRel = "standardReportCardType", path = "standardReportCardType")
public interface StandardReportCardTypeRepository extends AvniJpaRepository<StandardReportCardType, Long> {
    StandardReportCardType findByUuid(String uuid);

    List<StandardReportCardType> findAllByIsVoidedFalse();

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<StandardReportCardType> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable);

    boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime);

    List<StandardReportCardType> findAllByTypeIn(Set<StandardReportCardTypeType> defaultDashboardStandardCardTypeTypes);
}
