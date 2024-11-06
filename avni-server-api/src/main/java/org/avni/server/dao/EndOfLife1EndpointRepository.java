package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.Date;

/*
    * This is a marker interface for all the repositories that are used to fetch entities with old endpoints that do not serve new data after configured date.
    * Please also see org.avni.server.framework.sync.MetadataResourceInterceptor.endOfLifeEndpoints1
    * https://github.com/avniproject/avni-server/issues/782
    * same thing also done in StandardReportCardTypeRepository.java
 */
@NoRepositoryBean
public interface EndOfLife1EndpointRepository<T extends CHSEntity> {
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<T> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant now,
            Pageable pageable);
}
