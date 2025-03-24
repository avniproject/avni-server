package org.avni.server.dao.etl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    public List getProgramAndEncounterNames() {
        return entityManager.createNativeQuery("select name from table_metadata where type in ('Encounter', 'IndividualEncounterCancellation', 'ProgramEncounter', 'ProgramEncounterCancellation', 'ProgramEnrolment', 'ProgramExit')").getResultList();
    }

    public List getSubjectTypeNames() {
        return entityManager.createNativeQuery("select name from table_metadata where type in ('Individual', 'Group', 'Household')").getResultList();
    }
}
