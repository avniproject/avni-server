package org.avni.server.dao;

import org.avni.server.domain.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "news", path = "news")
public interface NewsRepository extends TransactionalDataRepository<News>, FindByLastModifiedDateTime<News> {

    News findByTitleAndIsVoidedFalse(String title);

    List<News> findByPublishedDateNotNullAndIsVoidedFalse();

    Page<News> findByPublishedDateNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(Instant lastModifiedDateTime, Instant now, Pageable pageable);

    Slice<News> findSliceByPublishedDateNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(Instant lastModifiedDateTime, Instant now, Pageable pageable);

    boolean existsByPublishedDateNotNullAndLastModifiedDateTimeGreaterThan(Instant lastModifiedDateTime);
}

