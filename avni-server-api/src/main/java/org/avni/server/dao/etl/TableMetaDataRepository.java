package org.avni.server.dao.etl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.avni.server.domain.Organisation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TableMetaDataRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public TableMetaDataRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List getProgramAndEncounterNames(Organisation organisation) {
        Query query = entityManager.createNativeQuery("select name from table_metadata where type in ('Encounter', 'IndividualEncounterCancellation', 'ProgramEncounter', 'ProgramEncounterCancellation', 'ProgramEnrolment', 'ProgramExit') and schema_name = :schemaName");
        query.setParameter("schemaName", organisation.getSchemaName());
        return query.getResultList();
    }

    public List getSubjectTypeNames(Organisation organisation) {
        Query query = entityManager.createNativeQuery("select name from table_metadata where type in ('Individual', 'Group', 'Household', 'Person') and schema_name = :schemaName");
        query.setParameter("schemaName", organisation.getSchemaName());
        return query.getResultList();
    }
}
