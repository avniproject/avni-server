package org.avni.server.dao.search;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.SubjectSearchRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestConceptService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestSubjectService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.webapp.search.PageDetails;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class CodedConceptFilterIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private SubjectSearchRepository subjectSearchRepository;

    private SubjectType subjectType;
    private TestDataSetupService.TestCatchmentData catchment;
    private Concept symptoms;
    private String fever;
    private String cough;
    private String rash;

    private void setUpOrganisation() {
        TestDataSetupService.TestOrganisationData org = testDataSetupService.setupOrganisation("codedfilter");
        setUser(org.getUser());
        catchment = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setName("Person codedfilter").build());
        symptoms = testConceptService.createCodedConcept("Symptoms codedfilter", "Fever", "Cough", "Rash");
        fever = answerUuid("Fever");
        cough = answerUuid("Cough");
        rash = answerUuid("Rash");
    }

    private String answerUuid(String name) {
        return symptoms.getConceptAnswers().stream().map(ConceptAnswer::getAnswerConcept)
                .filter(a -> a.getName().equals(name)).findFirst().get().getUuid();
    }

    private Individual subject(String name, Object answer) {
        Individual individual = new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(subjectType)
                .withLocation(catchment.getAddressLevel1()).withFirstName(name).build();
        ObservationCollection observations = new ObservationCollection();
        if (answer != null) observations.put(symptoms.getUuid(), answer);
        individual.setObservations(observations);
        return testSubjectService.save(individual);
    }

    private Set<String> search(String... answers) {
        SubjectSearchRequest request = new SubjectSearchRequest();
        request.setSubjectType(subjectType.getUuid());
        request.setIncludeVoided(false);
        request.setConcept(Collections.singletonList(new org.avni.server.web.request.webapp.search.Concept(
                symptoms.getUuid(), "registration", "Coded", Arrays.asList(answers), null)));
        PageDetails page = new PageDetails();
        page.setPageNumber(0);
        page.setNumberOfRecordPerPage(100);
        request.setPageElement(page);
        List<Map<String, Object>> rows = subjectSearchRepository.search(request, new SubjectSearchQueryBuilder());
        return rows.stream().map(row -> (String) row.get("uuid")).collect(Collectors.toSet());
    }

    @Test
    public void codedFilterMatchesSingleAndMultiSelectAnswers() {
        setUpOrganisation();
        Individual single = subject("Single", fever);
        Individual multi = subject("Multi", Arrays.asList(cough, fever));
        Individual other = subject("Other", rash);
        Individual multiOther = subject("MultiOther", Arrays.asList(cough, rash));
        subject("Unanswered", null);

        assertThat(search(fever)).containsExactlyInAnyOrder(single.getUuid(), multi.getUuid());
        assertThat(search(cough)).containsExactlyInAnyOrder(multi.getUuid(), multiOther.getUuid());
        assertThat(search(fever, rash)).containsExactlyInAnyOrder(single.getUuid(), multi.getUuid(), other.getUuid(), multiOther.getUuid());
        assertThat(search(answerUuid("Rash").replace('a', 'b'))).isEmpty();
    }
}
