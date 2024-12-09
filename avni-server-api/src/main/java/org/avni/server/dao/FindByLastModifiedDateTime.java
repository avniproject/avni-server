package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.Date;

public interface FindByLastModifiedDateTime<T> {
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<T> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant now,
            Pageable pageable);

    default Page<T> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            Date lastModifiedDateTime,
            Date now,
            Pageable pageable) {
        return findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(DateTimeUtil.toInstant(lastModifiedDateTime),
                DateTimeUtil.toInstant(now), pageable);
    }

    default Page<T> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            DateTime lastModifiedDateTime,
            DateTime now,
            Pageable pageable) {
        return findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(DateTimeUtil.toInstant(lastModifiedDateTime),
                DateTimeUtil.toInstant(now), pageable);
    }
}
