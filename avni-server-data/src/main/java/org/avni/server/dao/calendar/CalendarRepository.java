package org.avni.server.dao.calendar;

import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.ReferenceDataRepository;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.sync.CatchmentSyncSql;
import org.avni.server.domain.calendar.Calendar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;

@Repository
@RepositoryRestResource(collectionResourceRel = "calendar", path = "calendar")
public interface CalendarRepository extends ReferenceDataRepository<Calendar>, FindByLastModifiedDateTime<Calendar>, OperatingIndividualScopeAwareRepository<Calendar> {

    String CALENDAR_SCOPE_CLAUSE = " where c.organisation_id = :organisationId" +
            "  and (c.address_level_id is null or c.address_level_id in (" + CatchmentSyncSql.CATCHMENT_ADDRESS_LEVEL_IDS_SUBQUERY + ")) ";

    @Query(value = "select c.* from calendar c" + CALENDAR_SCOPE_CLAUSE +
            "  and c.last_modified_date_time between :lastModifiedDateTime and :now " +
            "order by c.last_modified_date_time asc, c.id asc",
            countQuery = "select count(*) from calendar c" + CALENDAR_SCOPE_CLAUSE +
                    "  and c.last_modified_date_time between :lastModifiedDateTime and :now",
            nativeQuery = true)
    Page<Calendar> getCalendarSyncResults(@Param("catchmentId") long catchmentId,
                                          @Param("organisationId") long organisationId,
                                          @Param("lastModifiedDateTime") Date lastModifiedDateTime,
                                          @Param("now") Date now,
                                          Pageable pageable);

    @Query(value = "select count(*) from calendar c" + CALENDAR_SCOPE_CLAUSE +
            "  and c.last_modified_date_time > :lastModifiedDateTime",
            nativeQuery = true)
    Long getCalendarChangedRowCount(@Param("catchmentId") long catchmentId,
                                    @Param("organisationId") long organisationId,
                                    @Param("lastModifiedDateTime") Date lastModifiedDateTime);

    @Override
    default Page<Calendar> getSyncResults(SyncParameters syncParameters) {
        return getCalendarSyncResults(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getLastModifiedDateTime().toDate(),
                syncParameters.getNow().toDate(),
                syncParameters.getPageable());
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return getCalendarChangedRowCount(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getLastModifiedDateTime().toDate()) > 0;
    }

    @Modifying(clearAutomatically = true)
    @Query("update Calendar c set c.isDefault = false where c.isDefault = true and c.uuid <> :uuid and c.isVoided = false")
    void clearDefaultExcept(@Param("uuid") String uuid);
}
