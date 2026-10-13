package org.avni.server.dao.attendance;

import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.TransactionalDataRepository;
import org.avni.server.dao.sync.CatchmentSyncSql;
import org.avni.server.domain.Individual;
import org.avni.server.domain.attendance.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "session", path = "session")
public interface SessionRepository extends TransactionalDataRepository<Session>, FindByLastModifiedDateTime<Session>, OperatingIndividualScopeAwareRepository<Session> {

    List<Session> findByGroupSubjectAndScheduledDateBetweenAndIsVoidedFalseOrderByScheduledDateAsc(
            Individual groupSubject, LocalDate from, LocalDate to);

    String SESSION_SCOPE_CLAUSE = " inner join individual gs on s.group_subject_id = gs.id" +
            " where s.organisation_id = :organisationId" +
            "  and gs.subject_type_id = :subjectTypeId" +
            "  and gs.address_id in (" + CatchmentSyncSql.CATCHMENT_ADDRESS_LEVEL_IDS_SUBQUERY + ") ";

    @Query(value = "select s.* from session s" + SESSION_SCOPE_CLAUSE +
            "  and s.last_modified_date_time between :lastModifiedDateTime and :now " +
            "order by s.last_modified_date_time asc, s.id asc",
            countQuery = "select count(*) from session s" + SESSION_SCOPE_CLAUSE +
                    "  and s.last_modified_date_time between :lastModifiedDateTime and :now",
            nativeQuery = true)
    Page<Session> getSessionSyncResults(@Param("catchmentId") long catchmentId,
                                        @Param("organisationId") long organisationId,
                                        @Param("subjectTypeId") long subjectTypeId,
                                        @Param("lastModifiedDateTime") Date lastModifiedDateTime,
                                        @Param("now") Date now,
                                        Pageable pageable);

    @Query(value = "select count(*) from session s" + SESSION_SCOPE_CLAUSE +
            "  and s.last_modified_date_time > :lastModifiedDateTime",
            nativeQuery = true)
    Long getSessionChangedRowCount(@Param("catchmentId") long catchmentId,
                                   @Param("organisationId") long organisationId,
                                   @Param("subjectTypeId") long subjectTypeId,
                                   @Param("lastModifiedDateTime") Date lastModifiedDateTime);

    @Override
    default Page<Session> getSyncResults(SyncParameters syncParameters) {
        return getSessionSyncResults(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getTypeId(),
                syncParameters.getLastModifiedDateTime().toDate(),
                syncParameters.getNow().toDate(),
                syncParameters.getPageable());
    }

    @Override
    default Slice<Session> getSyncResultsAsSlice(SyncParameters syncParameters) {
        return getSyncResults(syncParameters);
    }

    @Override
    default boolean isEntityChanged(SyncParameters syncParameters) {
        return getSessionChangedRowCount(
                syncParameters.getCatchment().getId(),
                syncParameters.getCatchment().getOrganisationId(),
                syncParameters.getTypeId(),
                syncParameters.getLastModifiedDateTime().toDate()) > 0;
    }
}
