package org.avni.server.web.api;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;

import static junit.framework.TestCase.assertEquals;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SubjectApiControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestConceptService testConceptService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private TestSubjectService testSubjectService;
    @Autowired
    private SubjectApiController subjectApiController;
    private SubjectType subjectType;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private Concept concept;

    @Before
    public void setUp() throws Exception {
        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        concept = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");
        ObservationCollection observationCollection = new ObservationCollectionBuilder()
                .addObservation(concept, concept.getAnswerConcept("Answer 1"))
                .build();
        testGroupService.giveViewSubjectPrivilegeTo(organisationData.getGroup(), subjectType);
        Individual subject = new SubjectBuilder()
                .withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1())
                .withObservations(observationCollection)
                .build();
        testSubjectService.save(subject);
    }

    @Test
    public void getSubjectsWithAllParams() {
        ResponsePage subjects = subjectApiController.getSubjects(DateTime.now().minusDays(1),
                DateTime.now().plusDays(1),
                subjectType.getName(),
                String.format("{\"%s\": \"%s\"}", concept.getName(), concept.getAnswerConcept("Answer 1").getUuid()),
                Collections.singletonList(catchmentData.getAddressLevel1().getUuid()),
                PageRequest.of(0, 10));
        assertEquals(1, subjects.getContent().size());
    }

    @Test
    public void getSubjectWithoutLastModifiedDateTime() {
        ResponsePage subjects = subjectApiController.getSubjects(null, null,
                subjectType.getName(),
                String.format("{\"%s\": \"%s\"}", concept.getName(), concept.getAnswerConcept("Answer 1").getUuid()),
                Collections.singletonList(catchmentData.getAddressLevel1().getUuid()),
                PageRequest.of(0, 10));
        assertEquals(1, subjects.getContent().size());
    }
}
