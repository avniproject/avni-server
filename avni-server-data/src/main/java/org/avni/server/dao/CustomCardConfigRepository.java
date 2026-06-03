package org.avni.server.dao;

import org.avni.server.domain.CustomCardConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "customCardConfig", path = "customCardConfig")
public interface CustomCardConfigRepository extends ReferenceDataRepository<CustomCardConfig> {

    List<CustomCardConfig> findAllByIsVoidedFalseOrderByName();

    CustomCardConfig findByNameIgnoreCaseAndIsVoidedFalse(String name);

    Page<CustomCardConfig> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            Date lastModifiedDateTime, Date now, Pageable pageable);
}
