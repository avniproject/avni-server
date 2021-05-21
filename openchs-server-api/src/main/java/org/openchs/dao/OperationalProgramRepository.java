package org.openchs.dao;

import org.joda.time.DateTime;
import org.openchs.domain.OperationalProgram;
import org.openchs.domain.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "operationalProgram", path = "operationalProgram")
public interface OperationalProgramRepository extends ImplReferenceDataRepository<OperationalProgram> {
    @RestResource(path = "lastModified", rel = "lastModified")
    @Query("select op from OperationalProgram op where op.audit.lastModifiedDateTime > :lastModifiedDateTime or op.program.audit.lastModifiedDateTime > :lastModifiedDateTime order by CASE WHEN op.program.audit.lastModifiedDateTime > op.audit.lastModifiedDateTime THEN op.program.audit.lastModifiedDateTime ELSE op.audit.lastModifiedDateTime END")
    Page<OperationalProgram> findByAuditLastModifiedDateTimeGreaterThanOrProgramAuditLastModifiedDateTimeGreaterThanOrderByAuditLastModifiedDateTimeAscIdAsc(@Param("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime, Pageable pageable);

    OperationalProgram findByProgramAndOrganisationId(Program program, long organisationId);

    @Query("select o.name from OperationalProgram o where o.isVoided = false")
    List<String> getAllNames();

}
