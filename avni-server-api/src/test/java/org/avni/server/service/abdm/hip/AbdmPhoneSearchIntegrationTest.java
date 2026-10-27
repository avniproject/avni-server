package org.avni.server.service.abdm.hip;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// DEMO-ONLY abdm-hackathon: never merged.
// Patient-led linking finds a patient by the phone Avni holds for them, read as IndividualService.findPhoneNumber reads
// it, so the number searched is the number the code is texted to. test-data.sql holds subject 4378dce3 (Male, org 2)
// with "9282738493" under the Text contact-number concept 7c39cb04.
@Sql(scripts = {"/test-data.sql"})
@Transactional
public class AbdmPhoneSearchIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String KNOWN = "4378dce3-247e-4393-8dd5-032c6eb0a655";
    private static final String CONTACT_NUMBER_CONCEPT = "7c39cb04-4f02-4c49-8f94-a5b697d40365";
    private static final String PHONE_NUMBER_CONCEPT = "3f2b8a64-6f0e-4d3a-9c1e-5a7d2b9e4c10";
    private static final String STORED_AS_PHONE_NUMBER = "8d1e6f2a-0b7c-4e5d-a3f9-2c4b6e8a1d07";
    private static final String STORED_WITH_LEADING_ZERO = "c5a9e3b7-1d2f-4a6c-8e0b-7f3d5a9c2e14";

    @Autowired
    private AbdmPatientPhones phones;
    @Autowired
    private AbdmHipStore store;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void aPatientIsFoundByTheLastTenDigitsOfThePhoneAvniHolds_inEitherForm() {
        JdbcOperations jdbc = namedJdbc.getJdbcOperations();
        jdbc.update("INSERT INTO concept (id, NAME, data_type, uuid, version, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                "VALUES (90001, 'Mobile', 'PhoneNumber', ?, 1, 1, 1, now(), now())", PHONE_NUMBER_CONCEPT);
        insertSubject(STORED_AS_PHONE_NUMBER, "{\"" + PHONE_NUMBER_CONCEPT + "\": {\"phoneNumber\": \"+91 92827 38493\", \"verified\": true}}");
        insertSubject(STORED_WITH_LEADING_ZERO, "{\"" + CONTACT_NUMBER_CONCEPT + "\": \"09282738493\"}");
        Integer year = jdbc.queryForObject("select extract(year from date_of_birth)::int from individual where uuid = ?", Integer.class, KNOWN);

        AbdmPatientPhones.PhoneConcepts concepts = phones.phoneConcepts();
        assertEquals(PHONE_NUMBER_CONCEPT, concepts.phoneNumberConceptUuid());
        assertEquals(CONTACT_NUMBER_CONCEPT, concepts.contactNumberConceptUuid());

        List<String> found = store.findSubjectsByPhone(concepts, "9282738493", "M", year).stream().map(AbdmHipStore.Candidate::uuid).toList();
        assertTrue(found.toString(), found.containsAll(List.of(KNOWN, STORED_AS_PHONE_NUMBER, STORED_WITH_LEADING_ZERO)));

        List<String> female = store.findSubjectsByPhone(concepts, "9282738493", "F", year).stream().map(AbdmHipStore.Candidate::uuid).toList();
        assertFalse(female.contains(KNOWN));
        assertTrue(store.findSubjectsByPhone(concepts, "9282738493", "M", year - 1).stream().noneMatch(c -> c.uuid().equals(KNOWN)));
        assertTrue(store.findSubjectsByPhone(concepts, "9000000000", "M", year).isEmpty());
    }

    private void insertSubject(String uuid, String observations) {
        namedJdbc.getJdbcOperations().update("INSERT INTO individual (uuid, address_id, version, date_of_birth, date_of_birth_verified, first_name, last_name, gender_id, organisation_id, " +
                "subject_type_id, observations, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                "VALUES (?, 1, 1, current_timestamp, FALSE, 'Prabhu', 'Kumar', 2, 2, (select id from subject_type where name = 'Individual'), ?::jsonb, 1, 1, now(), now())",
                uuid, observations);
    }
}
