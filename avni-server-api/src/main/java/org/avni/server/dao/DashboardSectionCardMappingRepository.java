package org.avni.server.dao;

import org.avni.server.domain.DashboardSection;
import org.avni.server.domain.DashboardSectionCardMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
@RepositoryRestResource(collectionResourceRel = "dashboardSectionCardMapping", path = "dashboardSectionCardMapping")
public interface DashboardSectionCardMappingRepository extends ReferenceDataRepository<DashboardSectionCardMapping> {

    default DashboardSectionCardMapping findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in FormMapping");
    }

    default DashboardSectionCardMapping findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in FormMapping");
    }

    DashboardSectionCardMapping findByCardIdAndDashboardSectionAndIsVoidedFalse(Long id, DashboardSection section);

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<DashboardSectionCardMapping> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);
}
