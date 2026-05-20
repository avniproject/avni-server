package org.avni.server.dao.attendance;

import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.TransactionalDataRepository;
import org.avni.server.dao.sync.CatchmentSyncSql;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "attendanceRecord", path = "attendanceRecord")
public interface AttendanceRecordRepository extends TransactionalDataRepository<AttendanceRecord>, FindByLastModifiedDateTime<AttendanceRecord>, OperatingIndividualScopeAwareRepository<AttendanceRecord> {

    List<AttendanceRecord> findBySessionAndIsVoidedFalse(Session session);

    List<AttendanceRecord> findBySessionInAndIsVoidedFalse(Collection<Session> sessions);

    String ATTENDANCE_RECORD_SCOPE_CLAUSE = " inner join session s on ar.session_id = s.id" +
            " inner join individual gs on s.group_subject_id = gs.id" +
            " where ar.organisation_id = :organisationId" +
            "  and gs.address_id in (" + CatchmentSyncSql.CATCHMENT_ADDRESS_LEVEL_IDS_SUBQUERY + ") ";

    @Query(value = "select ar.* from attendance_record ar" + ATTENDANCE_RECORD_SCOPE_CLAUSE +
            "  and ar.last_modified_date_time between :lastModifiedDateTime and :now " +
            "order by ar.last_modified_date_time asc, ar.id asc",
            countQuery = "select count(*) from attendance_record ar" + ATTENDANCE_RECORD_SCOPE_CLAUSE +
                    "  and ar.last_modified_date_time between :lastModifiedDateTime and :now",
            nativeQuery = true)
    Page<AttendanceRecord> getRecordSyncResults(@Param("catchmentId") long catchmentId,
                                                @Param("organisationId") long organisationId,
                                                @Param("lastModifiedDateTime") Date lastModifiedDateTime,
                                                @Param("now") Date now,
                                                Pageable pageable);

    @Query(value = "select count(*) from attendance_record ar" + ATTENDANCE_RECORD_SCOPE_CLAUSE +
            "  and ar.last_modified_date_time > :lastModifiedDateTime",
            nativeQuery = true)
    Long getRecordChangedRowCount(@Param("catchmentId") long catchmentId,
                                  @Param("organisationId") long organisationId,
                                  @Param("lastModifiedDateTime") Date lastModifiedDateTime);

    @Override
    default Page<AttendanceRecord> getSyncResults(SyncParameters syncParameters) {
        return getRecordSyncResults(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getLastModifiedDateTime().toDate(),
                syncParameters.getNow().toDate(),
                syncParameters.getPageable());
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return getRecordChangedRowCount(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getLastModifiedDateTime().toDate()) > 0;
    }
}
