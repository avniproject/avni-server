package org.avni.server.dao.calendar;

import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.ReferenceDataRepository;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.sync.CatchmentSyncSql;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "calendarDateMarker", path = "calendarDateMarker", exported = false)
public interface CalendarDateMarkerRepository extends ReferenceDataRepository<CalendarDateMarker>, FindByLastModifiedDateTime<CalendarDateMarker>, OperatingIndividualScopeAwareRepository<CalendarDateMarker> {

    List<CalendarDateMarker> findByCalendarAndIsVoidedFalse(Calendar calendar);

    List<CalendarDateMarker> findByCalendarAndMarkerDateBetweenAndIsVoidedFalse(Calendar calendar, LocalDate from, LocalDate to);

    List<CalendarDateMarker> findByMarkerDateBetweenAndIsVoidedFalse(LocalDate from, LocalDate to);

    String MARKER_SCOPE_CLAUSE = " inner join calendar c on cdm.calendar_id = c.id" +
            " where cdm.organisation_id = :organisationId" +
            "  and (c.address_level_id is null or c.address_level_id in (" + CatchmentSyncSql.CATCHMENT_ADDRESS_LEVEL_IDS_SUBQUERY + ")) ";

    @Query(value = "select cdm.* from calendar_date_marker cdm" + MARKER_SCOPE_CLAUSE +
            "  and cdm.last_modified_date_time between :lastModifiedDateTime and :now " +
            "order by cdm.last_modified_date_time asc, cdm.id asc",
            countQuery = "select count(*) from calendar_date_marker cdm" + MARKER_SCOPE_CLAUSE +
                    "  and cdm.last_modified_date_time between :lastModifiedDateTime and :now",
            nativeQuery = true)
    Page<CalendarDateMarker> getMarkerSyncResults(@Param("catchmentId") long catchmentId,
                                                  @Param("organisationId") long organisationId,
                                                  @Param("lastModifiedDateTime") Date lastModifiedDateTime,
                                                  @Param("now") Date now,
                                                  Pageable pageable);

    @Query(value = "select count(*) from calendar_date_marker cdm" + MARKER_SCOPE_CLAUSE +
            "  and cdm.last_modified_date_time > :lastModifiedDateTime",
            nativeQuery = true)
    Long getMarkerChangedRowCount(@Param("catchmentId") long catchmentId,
                                  @Param("organisationId") long organisationId,
                                  @Param("lastModifiedDateTime") Date lastModifiedDateTime);

    @Override
    default Page<CalendarDateMarker> getSyncResults(SyncParameters syncParameters) {
        return getMarkerSyncResults(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getLastModifiedDateTime().toDate(),
                syncParameters.getNow().toDate(),
                syncParameters.getPageable());
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return getMarkerChangedRowCount(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getLastModifiedDateTime().toDate()) > 0;
    }
}
