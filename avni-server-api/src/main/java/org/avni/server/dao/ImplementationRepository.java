package org.avni.server.dao;

import org.avni.server.application.projections.ReportingViewProjection;
import org.avni.server.domain.Organisation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImplementationRepository extends AvniCrudRepository<Organisation, Long> {
    @Query(value = "select create_view(:schemaName, :viewName, :sqlQuery)", nativeQuery = true)
    void createView(String schemaName, String viewName, String sqlQuery);

    @Query(value = "select viewname, definition from pg_views where schemaname = :schemaName", nativeQuery = true)
    List<ReportingViewProjection> getAllViewsInSchema(String schemaName);

    @Query(value = "select drop_view(:viewName, :schemaName)", nativeQuery = true)
    void dropView(String viewName, String schemaName);

    @Query(value = "select create_db_user(:name, :pass)", nativeQuery = true)
    void createDBUser(String name, String pass);

    @Query(value = "select create_implementation_schema(:schemaName, :dbUser)", nativeQuery = true)
    void createImplementationSchema(String schemaName, String dbUser);

    /**
     * This is kept here because OrganisationRepository has uer role level @PreAuthorize,
     * and findByName is required by customPrint API where org name is passed by the cookie
     */
    Organisation findByName(String name);
}
