package org.avni.server.dao;

import org.avni.server.domain.ReportCard;
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
@RepositoryRestResource(collectionResourceRel = "card", path = "card")
public interface CardRepository extends ReferenceDataRepository<ReportCard>, JpaSpecificationExecutor<ReportCard> {

    @Query("select c.name from ReportCard c where c.isVoided = false")
    List<String> getAllNames();

    List<ReportCard> findAllByIconFileS3KeyNotNull();

    List<ReportCard> findAllByIsVoidedFalseOrderByName();

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<ReportCard> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);
}
