package org.avni.server.dao.search;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.SubjectSearchRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SortOrder;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SearchAllIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private TestProgramEnrolmentService testProgramEnrolmentService;
    @Autowired
    private SubjectSearchRepository subjectSearchRepository;
    @Autowired
    private DataSource dataSource;

    private SubjectType subjectType;
    private Program program;
    private Concept answerConcept;
    private TestDataSetupService.TestCatchmentData catchment;

    private void setUpOrganisation(String suffix) {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation(suffix);
        setUser(org.getUser());
        catchment = testDataSetupService.setupACatchment();
        answerConcept = testConceptService.createConcept("Search all answer " + suffix, ConceptDataType.Text);
        subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Person " + suffix).build());
        program = testProgramService.addProgram(new ProgramBuilder().withName("Programme " + suffix).build(), subjectType);
    }

    private Individual subject(String firstName, String middleName, String lastName, String answer) {
        Individual individual = new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).withFirstName(firstName).build();
        individual.setMiddleName(middleName);
        individual.setLastName(lastName);
        if (answer != null) individual.setObservations(ObservationCollectionBuilder.withOneObservation(answerConcept, answer));
        return testSubjectService.save(individual);
    }

    private ProgramEnrolment enrol(Individual individual, String answer, boolean deleted) {
        ProgramEnrolment enrolment = new ProgramEnrolmentBuilder().withMandatoryFieldsForNewEntity().setProgram(program).setIndividual(individual)
                .setObservations(ObservationCollectionBuilder.withOneObservation(answerConcept, answer)).build();
        enrolment.setVoided(deleted);
        return testProgramEnrolmentService.save(enrolment);
    }

    private Set<String> searchAll(String text) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setSearchAll(text);
        request.setIncludeVoided(false);
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(100);
        page.setSortColumn("fullName");
        page.setSortOrder(SortOrder.asc);
        request.setPageElement(page);
        List<Map<String, Object>> rows = subjectSearchRepository.search(request, new SubjectSearchQueryBuilder());
        return rows.stream().map(row -> (String) row.get("uuid")).collect(Collectors.toSet());
    }

    @Test
    public void searchAllFindsSubjectsByNameAndAnswersAsThePhoneDoes() {
        setUpOrganisation("searchallb");
        Individual otherOrgSunita = subject("Sunita", null, "Test", "mango");

        setUpOrganisation("searchalla");
        Individual sunita = subject("Sunita", null, "Test", null);
        Individual enrolledSunita = subject("Sunita", "Kumari", "Rao", "nothing to see");
        enrol(enrolledSunita, "nothing here either", false);
        Individual blankMiddleName = subject("Asha", "", "Devi", null);
        Individual registrationAnswer = subject("Ravi", null, "Kumar", "mango orchard");
        Individual enrolmentAnswer = subject("Gopal", null, "Das", null);
        enrol(enrolmentAnswer, "papaya farm", false);
        Individual deletedEnrolmentAnswer = subject("Hari", null, "Lal", null);
        enrol(deletedEnrolmentAnswer, "guava grove", true);
        Individual percentInAnswer = subject("Mohan", null, "Singh", "100% done");

        assertThat(searchAll("sunita")).containsExactlyInAnyOrder(sunita.getUuid(), enrolledSunita.getUuid());
        assertThat(searchAll("Sunita Test")).containsExactly(sunita.getUuid());
        assertThat(searchAll("Sunita Kumari Rao")).containsExactly(enrolledSunita.getUuid());
        assertThat(searchAll("ita Kum")).containsExactly(enrolledSunita.getUuid());
        assertThat(searchAll("Test Sunita")).isEmpty();
        assertThat(searchAll("Asha Devi")).containsExactly(blankMiddleName.getUuid());
        assertThat(searchAll("mango")).containsExactly(registrationAnswer.getUuid());
        assertThat(searchAll("papaya")).containsExactly(enrolmentAnswer.getUuid());
        assertThat(searchAll("guava")).isEmpty();
        assertThat(searchAll("100%")).containsExactly(percentInAnswer.getUuid());
        assertThat(searchAll("%")).containsExactly(percentInAnswer.getUuid());
        assertThat(searchAll("S_nita")).isEmpty();

        assertThat(searchAll("sunita")).doesNotContain(otherOrgSunita.getUuid());
        assertThat(searchAll("mango")).doesNotContain(otherOrgSunita.getUuid());
    }

    @Test
    public void searchAllMatchesAnswersAsTypedNotTheirStoredJson() {
        setUpOrganisation("searchallc");
        Individual quoted = subject("Quoted", null, "Answer", "said \"hello\" twice");
        Individual backslash = subject("Back", null, "Slash", "C:\\temp\\file");
        Individual multiline = subject("Multi", null, "Line", "first line\nsecond line");
        Individual punctuation = subject("Comma", null, "Colon", "red, green: blue");
        Individual numeric = subject("Numeric", null, "Answer", null);
        numeric.setObservations(ObservationCollectionBuilder.withOneObservation(answerConcept, 4521));
        testSubjectService.save(numeric);
        Individual coded = subject("Coded", null, "Answer", "0f8c1a77-2b5e-4c3d-9a1b-7e6d5c4b3a21");
        Individual grouped = subject("Grouped", null, "Answer", null);
        ObservationCollection groupAnswer = new ObservationCollection();
        groupAnswer.put("6a5b4c3d-1111-2222-3333-444455556666", "nested tamarind");
        grouped.setObservations(ObservationCollectionBuilder.withOneObservation(answerConcept, Collections.singletonList(groupAnswer)));
        testSubjectService.save(grouped);
        subject("Plain", null, "Answer", "nothing special");
        Individual yes = subject("Yes", null, "Answer", null);
        yes.setObservations(ObservationCollectionBuilder.withOneObservation(answerConcept, true));
        testSubjectService.save(yes);
        Individual decimal = subject("Decimal", null, "Answer", null);
        decimal.setObservations(ObservationCollectionBuilder.withOneObservation(answerConcept, new java.math.BigDecimal("12.5")));
        testSubjectService.save(decimal);
        Individual regexCharacters = subject("Regex", null, "Answer", "a.b*c (x)");

        assertThat(searchAll("said \"hello\"")).containsExactly(quoted.getUuid());
        assertThat(searchAll("\"")).containsExactly(quoted.getUuid());
        assertThat(searchAll("C:\\temp")).containsExactly(backslash.getUuid());
        assertThat(searchAll("\\")).containsExactly(backslash.getUuid());
        assertThat(searchAll("line\nsecond")).containsExactly(multiline.getUuid());
        assertThat(searchAll("red, green")).containsExactly(punctuation.getUuid());
        assertThat(searchAll(",")).containsExactly(punctuation.getUuid());
        assertThat(searchAll(":")).containsExactlyInAnyOrder(punctuation.getUuid(), backslash.getUuid());
        assertThat(searchAll("4521")).containsExactly(numeric.getUuid());
        assertThat(searchAll("tamarind")).containsExactly(grouped.getUuid());
        assertThat(searchAll(answerConcept.getUuid().substring(0, 8))).isEmpty();
        assertThat(searchAll("6a5b4c3d")).isEmpty();
        assertThat(searchAll("0f8c1a77")).isEmpty();
        assertThat(searchAll("\\n")).isEmpty();
        assertThat(searchAll("nothing\u0000 special")).hasSize(1);
        assertThat(searchAll("452")).isEmpty();
        assertThat(searchAll("12.5")).containsExactly(decimal.getUuid());
        assertThat(searchAll("TRUE")).containsExactly(yes.getUuid());
        assertThat(searchAll("tru")).isEmpty();
        assertThat(searchAll("false")).isEmpty();
        assertThat(searchAll("a.b*c (x)")).containsExactly(regexCharacters.getUuid());
        assertThat(searchAll("a.b")).containsExactly(regexCharacters.getUuid());
        assertThat(searchAll(".*")).isEmpty();
        assertThat(searchAll("SAID \"HELLO\"")).containsExactly(quoted.getUuid());
    }

    @Test
    public void resultNameHasNoDoubleSpaceWhenTheMiddleNameIsBlank() {
        setUpOrganisation("searchalld");
        Individual blankMiddleName = subject("Asha", "", "Devi", null);

        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setName("asha");
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(10);
        page.setSortColumn("fullName");
        page.setSortOrder(SortOrder.asc);
        request.setPageElement(page);
        List<Map<String, Object>> rows = subjectSearchRepository.search(request, new SubjectSearchQueryBuilder());

        assertThat(rows).extracting(row -> row.get("uuid")).containsExactly(blankMiddleName.getUuid());
        assertThat(rows.get(0).get("fullName")).isEqualTo("Asha Devi");
    }

    @Test
    public void nulCharacterInAnyTextFilterDoesNotFailTheSearch() {
        setUpOrganisation("searchalle");
        Individual asha = subject("Asha", null, "Devi", "mango orchard");

        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setName("as\u0000ha");
        request.setSearchAll("man\u0000go");
        request.setConcept(Collections.singletonList(new org.avni.server.web.request.webapp.search.Concept(answerConcept.getUuid(), "registration", "Text", null, "orch\u0000ard")));
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(10);
        request.setPageElement(page);

        assertThat(subjectSearchRepository.search(request, new SubjectSearchQueryBuilder()))
                .extracting(row -> row.get("uuid")).containsExactly(asha.getUuid());
        assertThat(subjectSearchRepository.getTotalCount(request, new SubjectSearchQueryBuilder())).isEqualTo(1L);
    }

    @Test
    public void typedTextIsEscapedExactlyAsPostgresStoresItInJson() {
        StringBuilder every = new StringBuilder();
        for (char c = 1; c < 0x80; c++) every.append(c);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String value : List.of(every.toString(), "सुनीता देवी", "Ünïcödé 😀 \u2028 \u007f")) {
            String stored = jdbc.queryForObject("select cast(to_jsonb(cast(? as text)) as text)", String.class, value);
            assertThat("\"" + BaseSubjectSearchQueryBuilder.asStoredInJson(value) + "\"").isEqualTo(stored);
        }
    }
}
