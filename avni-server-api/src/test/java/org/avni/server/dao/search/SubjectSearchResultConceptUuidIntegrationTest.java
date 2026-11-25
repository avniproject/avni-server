package org.avni.server.dao.search;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.SubjectSearchRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SortOrder;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Found while fixing avni-security#97: a search-result column's concept uuid was pasted into the subject
 * search SQL as a string literal, and that query runs with row-level security off. A concept's uuid is
 * whatever the concept save request sent, so a crafted uuid ran as part of the query. These tests run
 * every column shape and both callers of the changed code against the database, list and count.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SubjectSearchResultConceptUuidIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String CRAFTED_UUID = "c', (select count(*) from individual) as \"leak\", '";

    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private ConceptRepository conceptRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private SubjectSearchRepository subjectSearchRepository;
    @Autowired
    private UserSubjectAssignmentService userSubjectAssignmentService;

    private SubjectType subjectType;
    private TestDataSetupService.TestCatchmentData catchment;
    private long organisationId;
    private final List<Map<String, Object>> searchResultFields = new ArrayList<>();

    private void setUpOrganisation(String suffix) {
        setUpOrganisation(suffix, null);
    }

    private void setUpOrganisation(String suffix, Concept syncConcept) {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation(suffix);
        setUser(org.getUser());
        organisationId = org.getOrganisationId();
        catchment = testDataSetupService.setupACatchment();
        subjectType = subjectTypeNamed("Person " + suffix, syncConcept);
    }

    private SubjectType subjectTypeNamed(String name, Concept syncConcept) {
        SubjectType newType = new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName(name).build();
        if (syncConcept != null) newType.setSyncRegistrationConcept1(syncConcept.getUuid());
        return testSubjectTypeService.createWithDefaults(newType);
    }

    private Concept concept(String uuid, String name, ConceptDataType dataType) {
        return conceptRepository.save(new ConceptBuilder().withUuid(uuid).withName(name).withDataType(dataType).build());
    }

    private String answerUuid(Concept coded, String answerName) {
        return coded.getConceptAnswers().stream().map(ConceptAnswer::getAnswerConcept)
                .filter(c -> c.getName().equals(answerName)).findFirst().orElseThrow().getUuid();
    }

    private void showAsSearchResultColumns(SubjectType forSubjectType, Concept... concepts) {
        List<Map<String, Object>> resultConcepts = new ArrayList<>();
        for (int i = 0; i < concepts.length; i++) {
            Map<String, Object> resultConcept = new LinkedHashMap<>();
            resultConcept.put("uuid", concepts[i].getUuid());
            resultConcept.put("name", concepts[i].getName());
            resultConcept.put("displayOrder", i + 1);
            resultConcepts.add(resultConcept);
        }
        Map<String, Object> searchField = new LinkedHashMap<>();
        searchField.put("subjectTypeUUID", forSubjectType.getUuid());
        searchField.put("searchResultConcepts", resultConcepts);
        searchResultFields.add(searchField);
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisationId);
        config.getSettings().put(OrganisationConfigSettingKey.searchResultFields.name(), new ArrayList<>(searchResultFields));
        config.updateLastModifiedDateTime();
        organisationConfigRepository.save(config);
    }

    private Individual subject(SubjectType ofType, String firstName, Map<Concept, Object> answers) {
        Individual individual = new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(ofType)
                .withLocation(catchment.getAddressLevel1()).withFirstName(firstName).build();
        ObservationCollection observations = new ObservationCollection();
        answers.forEach((concept, answer) -> observations.putAll(ObservationCollectionBuilder.withOneObservation(concept, answer)));
        individual.setObservations(observations);
        return testSubjectService.save(individual);
    }

    private Individual subject(String firstName, Concept concept, Object answer) {
        return subject(subjectType, firstName, Map.of(concept, answer));
    }

    private SubjectSearchRequest request(SubjectType ofType) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(ofType == null ? null : ofType.getUuid());
        request.setIncludeVoided(false);
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(10);
        page.setSortColumn("fullName");
        page.setSortOrder(SortOrder.asc);
        request.setPageElement(page);
        return request;
    }

    private List<Map<String, Object>> search(SubjectType ofType) {
        return subjectSearchRepository.search(request(ofType), new SubjectSearchQueryBuilder());
    }

    private Long count(SubjectType ofType) {
        return subjectSearchRepository.getTotalCount(request(ofType), new SubjectSearchQueryBuilder());
    }

    @Test
    public void aSearchResultColumnStillShowsTheAnswer() {
        setUpOrganisation("sec97d");
        Concept village = concept("2d6f3c1a-97d0-4c1e-9a6e-0b7a1f2e3d4c", "Village name", ConceptDataType.Text);
        showAsSearchResultColumns(subjectType, village);
        subject("Asha", village, "Kodathi");

        List<Map<String, Object>> rows = search(subjectType);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("Village name")).isEqualTo("Kodathi");
        assertThat(count(subjectType)).isEqualTo(1L);
    }

    @Test
    public void aCraftedConceptUuidDoesNotRunAsPartOfTheQuery() {
        setUpOrganisation("sec97e");
        Concept crafted = concept(CRAFTED_UUID, "Crafted column", ConceptDataType.Text);
        showAsSearchResultColumns(subjectType, crafted);
        subject("Ravi", crafted, "an answer");

        List<Map<String, Object>> rows = search(subjectType);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKey("leak");
        assertThat(rows.get(0).get("Crafted column")).isEqualTo("an answer");
        assertThat(count(subjectType)).isEqualTo(1L);
    }

    @Test
    public void codedAndDateColumnsShowTheirAnswers() {
        setUpOrganisation("sec97f");
        Concept symptoms = testConceptService.createCodedConcept("Symptoms", "Fever", "Cough");
        Concept lmp = concept(UUID.randomUUID().toString(), "LMP", ConceptDataType.Date);
        showAsSearchResultColumns(subjectType, symptoms, lmp);
        Map<Concept, Object> answers = new LinkedHashMap<>();
        answers.put(symptoms, List.of(answerUuid(symptoms, "Fever")));
        answers.put(lmp, "2026-11-01T10:00:00.000Z");
        subject(subjectType, "Sita", answers);

        List<Map<String, Object>> rows = search(subjectType);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("Symptoms")).isEqualTo("Fever");
        assertThat(String.valueOf(rows.get(0).get("LMP"))).isEqualTo("2026-11-01");
        assertThat(count(subjectType)).isEqualTo(1L);
    }

    @Test
    public void oneConceptShownForTwoSubjectTypesGivesOneColumnWhenNoSubjectTypeIsChosen() {
        setUpOrganisation("sec97g");
        SubjectType household = subjectTypeNamed("Household sec97g", null);
        Concept village = concept(UUID.randomUUID().toString(), "Village name", ConceptDataType.Text);
        showAsSearchResultColumns(subjectType, village);
        showAsSearchResultColumns(household, village);
        subject("Asha", village, "Kodathi");
        subject(household, "Rao family", Map.of(village, "Mullur"));

        SqlQuery query = new SubjectSearchQueryBuilder().withSubjectSearchFilter(request(null), null).build(null);
        List<Map<String, Object>> rows = search(null);

        assertThat(query.getParameters()).containsKey("customFieldConcept0").doesNotContainKey("customFieldConcept1");
        assertThat(rows).extracting(row -> row.get("Village name")).containsExactlyInAnyOrder("Kodathi", "Mullur");
        assertThat(count(null)).isEqualTo(2L);
    }

    @Test
    public void searchWithNoSubjectTypeBindsNoSubjectTypeName() {
        setUpOrganisation("sec97h");
        Concept village = concept(UUID.randomUUID().toString(), "Village name", ConceptDataType.Text);
        showAsSearchResultColumns(subjectType, village);
        subject("Asha", village, "Kodathi");

        SqlQuery query = new SubjectSearchQueryBuilder().withSubjectSearchFilter(request(null), null).build(null);
        List<Map<String, Object>> rows = search(null);

        assertThat(query.getParameters()).doesNotContainKey("subjectTypeName");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("subjectTypeName")).isEqualTo(subjectType.getName());
        assertThat(rows.get(0).get("Village name")).isEqualTo("Kodathi");
        assertThat(count(null)).isEqualTo(1L);
    }

    @Test
    public void subjectAssignmentSearchShowsCodedSyncAttribute() {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation("sec97i");
        setUser(org.getUser());
        organisationId = org.getOrganisationId();
        catchment = testDataSetupService.setupACatchment();
        Concept school = testConceptService.createCodedConcept("School", "Mullur", "Haralur");
        subjectType = subjectTypeNamed("Student sec97i", school);
        subject("Asha", school, answerUuid(school, "Mullur"));

        LinkedHashMap<String, Object> result = userSubjectAssignmentService.searchSubjects(request(subjectType));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("listOfRecords");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("School")).isEqualTo("Mullur");
        assertThat(result.get("totalElements")).isEqualTo(1L);
    }

    @Test
    public void subjectAssignmentSearchDoesNotRunACraftedSyncAttributeUuid() {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation("sec97j");
        setUser(org.getUser());
        organisationId = org.getOrganisationId();
        catchment = testDataSetupService.setupACatchment();
        Concept crafted = concept(CRAFTED_UUID, "Crafted sync", ConceptDataType.Text);
        subjectType = subjectTypeNamed("Student sec97j", crafted);
        subject("Ravi", crafted, "an answer");

        LinkedHashMap<String, Object> result = userSubjectAssignmentService.searchSubjects(request(subjectType));

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("listOfRecords");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKey("leak");
        assertThat(rows.get(0).get("Crafted sync")).isEqualTo("an answer");
        assertThat(result.get("totalElements")).isEqualTo(1L);
    }
}
