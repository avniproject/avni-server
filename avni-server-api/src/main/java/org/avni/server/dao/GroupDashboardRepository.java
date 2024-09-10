package org.avni.server.dao;

import org.avni.server.domain.GroupDashboard;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "groupDashboard", path = "groupDashboard")
public interface GroupDashboardRepository extends ReferenceDataRepository<GroupDashboard> {
    default GroupDashboard findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupDashboard");
    }

    default GroupDashboard findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in GroupDashboard");
    }

    List<GroupDashboard> findByGroup_IdAndIsVoidedFalseOrderByDashboardName(Long groupId);

    List<GroupDashboard> findByGroup_IdAndIdNotAndIsVoidedFalse(Long groupId, Long Id);

    @RestResource(path = "lastModified", rel = "lastModified")
    Page<GroupDashboard> findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
            @Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date lastModifiedDateTime,
            @Param("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date now,
            Pageable pageable);
}
