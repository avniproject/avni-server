package org.avni.server.dao;

import org.avni.server.domain.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(collectionResourceRel = "approvalStatus", path = "approvalStatus")
public interface ApprovalStatusRepository extends AvniJpaRepository<ApprovalStatus, Long> {
    @RestResource(path = "lastModified", rel = "lastModified")
    Page<ApprovalStatus> findByLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable);

    ApprovalStatus findByUuid(String uuid);

    ApprovalStatus findByStatus(ApprovalStatus.Status status);

    boolean existsByLastModifiedDateTimeGreaterThan(DateTime lastModifiedDateTime);
}
