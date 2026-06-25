package org.avni.server.dao;

import org.avni.server.domain.DownloadableContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "downloadableContent", path = "downloadableContent")
public interface DownloadableContentRepository extends ReferenceDataRepository<DownloadableContent> {

    List<DownloadableContent> findAllByIsVoidedFalseOrderByName();

    DownloadableContent findByNameIgnoreCaseAndIsVoidedFalse(String name);

    Page<DownloadableContent> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            Date lastModifiedDateTime, Date now, Pageable pageable);
}
