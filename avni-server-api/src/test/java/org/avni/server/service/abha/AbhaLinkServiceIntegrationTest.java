package org.avni.server.service.abha;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.service.IndividualService;
import org.avni.server.service.builder.TestConceptService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Not @Transactional: each save runs in its own committed transaction, as it does on the server, and the organisation
 * role is applied when a connection is borrowed. tear-down.sql cleans up around each test.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AbhaLinkServiceIntegrationTest extends AbstractControllerIntegrationTest {
    private static final Long DEMO_ID = 2L;
    // test-data.sql: the default subject type and a location, both OpenCHS's, which demo (its child) uses.
    private static final String SUBJECT_TYPE = "9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3";
    private static final String LOCATION = "ae35fe6d-910e-47bd-a0c7-0c10182a4085";
    private static final String ABHA_NUMBER = "12-3456-7856-8912";
    private static final String ABHA_DIGITS = "12345678568912";
    private static final String ABHA_ADDRESS = "ramesh.gond@sbx";

    @Autowired
    private IndividualService individualService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    private Concept abhaNumber;
    private Concept abhaAddress;

    @Before
    public void createAbhaQuestions() {
        setUser("demo-admin");
        abhaNumber = testConceptService.createConcept("ABHA Number", ConceptDataType.Text);
        abhaAddress = testConceptService.createConcept("ABHA Address", ConceptDataType.Text);
        setUser("demo-user");
    }

    @Test
    public void saveWithAnswerCreatesLink() {
        enableAbha();

        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        assertEquals(Collections.singletonList(live(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    @Test
    public void abhaDisabledCreatesNothing() {
        save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        assertEquals(Collections.emptyList(), links());
    }

    // Updated in place: the subject keeps one row, which now holds the new number.
    @Test
    public void changedAnswerUpdatesLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.getObservations().put(abhaNumber.getUuid(), "91-1111-2222-3333");
        subject.getObservations().put(abhaAddress.getUuid(), "ramesh.g@abdm");
        save(subject);

        assertEquals(Collections.singletonList(live(subject, "91111122223333", "ramesh.g@abdm")), links());
    }

    @Test
    public void removedAnswerVoidsLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.getObservations().remove(abhaNumber.getUuid());
        subject.getObservations().remove(abhaAddress.getUuid());
        save(subject);

        assertEquals(Collections.singletonList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // DELETE /web/subject/{uuid} and the external API void through IndividualService.voidSubject.
    @Test
    public void voidedSubjectVoidsLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        new TransactionTemplate(transactionManager).execute(status -> individualService.voidSubject(subject));

        assertEquals(Collections.singletonList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // The app voids a subject by pushing it with voided set, through save.
    @Test
    public void subjectVoidedBySyncVoidsLink() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));

        subject.setVoided(true);
        save(subject);

        assertEquals(Collections.singletonList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    @Test
    public void restoredSubjectRelinks() {
        enableAbha();
        Individual subject = save(subjectWith(ABHA_NUMBER, ABHA_ADDRESS));
        subject.setVoided(true);
        save(subject);

        subject.setVoided(false);
        save(subject);

        assertEquals(Arrays.asList(voided(subject, ABHA_DIGITS, ABHA_ADDRESS), live(subject, ABHA_DIGITS, ABHA_ADDRESS)), links());
    }

    // The app's route: a registration pushed by sync.
    @Test
    public void syncPushWithAnswerCreatesLink() {
        enableAbha();
        String uuid = "6b0d3ea2-90a4-4c39-8a5e-0d9cbb2c1f01";
        Map<String, Object> request = new HashMap<>();
        request.put("uuid", uuid);
        request.put("firstName", "Ramesh");
        request.put("lastName", "Gond");
        request.put("dateOfBirth", "1997-08-21");
        request.put("gender", "Male");
        request.put("addressLevelUUID", LOCATION);
        request.put("registrationDate", "2018-03-06");
        request.put("observations", Arrays.asList(observation(abhaNumber, ABHA_NUMBER), observation(abhaAddress, ABHA_ADDRESS)));

        post("/individuals", request);

        assertEquals(Collections.singletonList(uuid + " " + ABHA_DIGITS + " " + ABHA_ADDRESS + " org " + DEMO_ID + " live"), links());
    }

    private static Map<String, Object> observation(Concept concept, Object value) {
        Map<String, Object> observation = new HashMap<>();
        observation.put("conceptUUID", concept.getUuid());
        observation.put("value", value);
        return observation;
    }

    private Individual subjectWith(String number, String address) {
        ObservationCollection observations = new ObservationCollection();
        if (number != null) observations.put(abhaNumber.getUuid(), number);
        if (address != null) observations.put(abhaAddress.getUuid(), address);
        SubjectType subjectType = subjectTypeRepository.findByUuid(SUBJECT_TYPE);
        AddressLevel location = locationRepository.findByUuid(LOCATION);
        return new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType).withLocation(location)
                .withObservations(observations).build();
    }

    private Individual save(Individual subject) {
        return new TransactionTemplate(transactionManager).execute(status -> individualService.save(subject));
    }

    private static String live(Individual subject, String number, String address) {
        return subject.getUuid() + " " + number + " " + address + " org " + DEMO_ID + " live";
    }

    private static String voided(Individual subject, String number, String address) {
        return subject.getUuid() + " " + number + " " + address + " org " + DEMO_ID + " voided";
    }

    // Every row, live or voided, as "<subject uuid> <number> <address> org <id> live|voided", in creation order.
    private List<String> links() {
        return new JdbcTemplate(dataSource).query(
                "select i.uuid, l.abha_number, l.abha_address, l.organisation_id, l.is_voided from public.abha_link l " +
                        "join public.individual i on i.id = l.individual_id order by l.id",
                (rs, rowNum) -> rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3) + " org " + rs.getLong(4)
                        + (rs.getBoolean(5) ? " voided" : " live"));
    }

    private void enableAbha() {
        new JdbcTemplate(dataSource).update("update organisation_config set settings = settings || '{\"enableAbha\": true}'::jsonb where organisation_id = ?", DEMO_ID);
    }
}
