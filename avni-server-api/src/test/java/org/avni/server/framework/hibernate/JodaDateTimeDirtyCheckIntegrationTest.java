package org.avni.server.framework.hibernate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestEncounterTypeService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.util.ObjectMapperSingleton;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class JodaDateTimeDirtyCheckIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestEncounterTypeService testEncounterTypeService;
    @Autowired
    private EncounterRepository encounterRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Encounter encounter;

    @Before
    public void setupData() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        TestDataSetupService.TestCatchmentData catchmentData = testDataSetupService.setupACatchment();
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setType(Subject.Person).setName("Person").build());
        EncounterType encounterType = testEncounterTypeService.addGeneralEncounterTypeAndGetFormMapping("General Visit", subjectType).getEncounterType();
        setUser(organisationData.getUser().getUsername());
        Individual subject = testSubjectService.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(catchmentData.getAddressLevel1()).build());

        encounter = new Encounter();
        encounter.assignUUID();
        encounter.setEncounterType(encounterType);
        encounter.setIndividual(subject);
        encounter.setAddressId(catchmentData.getAddressLevel1().getId());
        encounter.setEncounterDateTime(DateTime.now(), organisationData.getUser());
        encounter.setObservations(new ObservationCollection());
        encounter = encounterRepository.save(encounter);
        entityManager.flush();
    }

    @Test
    public void timestampSetOnTheServerIsNotDirtyAfterFlush() {
        assertEquals(List.of(), dirtyProperties(encounter));
    }

    @Test
    public void sameInstantFromTheClientIsNotDirty() throws Exception {
        Encounter loaded = reload();
        String clientValue = "\"" + loaded.getEncounterDateTime().withZone(DateTimeZone.forOffsetHoursMinutes(5, 30)) + "\"";
        loaded.setEncounterDateTime(ObjectMapperSingleton.getObjectMapper().readValue(clientValue, DateTime.class), null);

        assertEquals(List.of(), dirtyProperties(loaded));
    }

    @Test
    public void differentInstantIsDirty() {
        Encounter loaded = reload();
        loaded.setEncounterDateTime(loaded.getEncounterDateTime().plusMinutes(1), null);

        assertEquals(List.of("encounterDateTime"), dirtyProperties(loaded));
    }

    @Test
    public void readBackIsInTheServersZone() {
        assertEquals(DateTime.now().getZone(), reload().getEncounterDateTime().getZone());
    }

    private Encounter reload() {
        entityManager.clear();
        return encounterRepository.findByUuid(encounter.getUuid());
    }

    private List<String> dirtyProperties(Object entity) {
        SessionImplementor session = entityManager.unwrap(SessionImplementor.class);
        EntityEntry entry = session.getPersistenceContextInternal().getEntry(entity);
        EntityPersister persister = entry.getPersister();
        int[] dirty = persister.findDirty(persister.getValues(entity), entry.getLoadedState(), entity, session);
        if (dirty == null) return List.of();
        return Arrays.stream(dirty).mapToObj(index -> persister.getPropertyNames()[index]).toList();
    }
}
