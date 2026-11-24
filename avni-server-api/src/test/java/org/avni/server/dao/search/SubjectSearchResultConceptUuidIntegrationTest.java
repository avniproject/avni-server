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
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SortOrder;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Found while fixing avni-security#97: a search-result column's concept uuid was pasted into the subject
 * search SQL as a string literal, and that query runs with row-level security off. A concept's uuid is
 * whatever the concept save request sent, so a crafted uuid ran as part of the query.
 */
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SubjectSearchResultConceptUuidIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private ConceptRepository conceptRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private SubjectSearchRepository subjectSearchRepository;

    private SubjectType subjectType;
    private TestDataSetupService.TestCatchmentData catchment;
    private long organisationId;

    private void setUpOrganisation(String suffix) {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation(suffix);
        setUser(org.getUser());
        organisationId = org.getOrganisationId();
        catchment = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Person " + suffix).build());
    }

    private Concept textConcept(String uuid, String name) {
        return conceptRepository.save(new ConceptBuilder().withUuid(uuid).withName(name).withDataType(ConceptDataType.Text).build());
    }

    private void showAsSearchResultColumn(Concept concept) {
        Map<String, Object> resultConcept = new LinkedHashMap<>();
        resultConcept.put("uuid", concept.getUuid());
        resultConcept.put("name", concept.getName());
        resultConcept.put("displayOrder", 1);
        Map<String, Object> searchField = new LinkedHashMap<>();
        searchField.put("subjectTypeUUID", subjectType.getUuid());
        searchField.put("searchResultConcepts", List.of(resultConcept));
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisationId);
        config.getSettings().put(OrganisationConfigSettingKey.searchResultFields.name(), List.of(searchField));
        config.updateLastModifiedDateTime();
        organisationConfigRepository.save(config);
    }

    private Individual subject(String firstName, Concept concept, String answer) {
        Individual individual = new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).withFirstName(firstName).build();
        individual.setObservations(ObservationCollectionBuilder.withOneObservation(concept, answer));
        return testSubjectService.save(individual);
    }

    private List<Map<String, Object>> search() {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setIncludeVoided(false);
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(10);
        page.setSortColumn("fullName");
        page.setSortOrder(SortOrder.asc);
        request.setPageElement(page);
        return subjectSearchRepository.search(request, new SubjectSearchQueryBuilder());
    }

    @Test
    public void aSearchResultColumnStillShowsTheAnswer() {
        setUpOrganisation("sec97d");
        Concept village = textConcept("2d6f3c1a-97d0-4c1e-9a6e-0b7a1f2e3d4c", "Village name");
        showAsSearchResultColumn(village);
        subject("Asha", village, "Kodathi");

        List<Map<String, Object>> rows = search();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("Village name")).isEqualTo("Kodathi");
    }

    @Test
    public void aCraftedConceptUuidDoesNotRunAsPartOfTheQuery() {
        setUpOrganisation("sec97e");
        Concept crafted = textConcept("c', (select count(*) from individual) as \"leak\", '", "Crafted column");
        showAsSearchResultColumn(crafted);
        subject("Ravi", crafted, "an answer");

        List<Map<String, Object>> rows = search();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).doesNotContainKey("leak");
        assertThat(rows.get(0).get("Crafted column")).isEqualTo("an answer");
    }
}
