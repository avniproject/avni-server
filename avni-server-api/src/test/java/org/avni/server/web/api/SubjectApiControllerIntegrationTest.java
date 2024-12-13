package org.avni.server.web.api;

import jakarta.transaction.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;


@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
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
    private Concept singleCodedConcept;
    private Concept multiCodedConcept;
    private Concept numericConcept;
    private Concept textConcept;
    private Concept locationConcept;
    private Concept dateConcept;

    @Before
    public void setUp() throws Exception {
        DateTimeZone.setDefault(DateTimeZone.forOffsetHoursMinutes(5, 30));

        ApiRequestContextHolder.create(new ApiRequestContext("2"));

        TestDataSetupService.TestOrganisationData organisationData = testDataSetupService.setupOrganisation();
        catchmentData = testDataSetupService.setupACatchment();
        subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectType1")
                        .setName("subjectType1")
                        .build());
        singleCodedConcept = testConceptService.createCodedConcept("Single Coded", "singleCoded1", "singleCoded2");
        multiCodedConcept = testConceptService.createCodedConcept("Multi Coded", "multiCoded1", "multiCoded2", "multiCoded3");
        numericConcept = testConceptService.createConcept("Numeric", ConceptDataType.Numeric);
        textConcept = testConceptService.createConcept("Text", ConceptDataType.Text);
        locationConcept = testConceptService.createConcept("Location", ConceptDataType.Location);
        dateConcept = testConceptService.createConcept("Date", ConceptDataType.Date);
        ObservationCollection observationCollection = new ObservationCollectionBuilder()
                .addObservation(singleCodedConcept, singleCodedConcept.getAnswerConcept("singleCoded1"))
                .addMultiCodedObservation(multiCodedConcept, multiCodedConcept.getAnswerConcept("multiCoded1"), multiCodedConcept.getAnswerConcept("multiCoded2"))
                .addObservation(numericConcept, 10)
                .addObservation(textConcept, "Hello world")
                .addObservation(locationConcept, catchmentData.getAddressLevel1().getUuid())
                .addObservation(dateConcept, "2000-10-31T18:30:00.000+00:00")
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
                String.format("{\"%s\": \"%s\"}", singleCodedConcept.getName(), singleCodedConcept.getAnswerConcept("singleCoded1").getUuid()),
                Collections.singletonList(catchmentData.getAddressLevel1().getUuid()),
                true,
                PageRequest.of(0, 10));
        assertEquals(1, subjects.getContent().size());

        Map subject = (Map) subjects.getContent().get(0);
        Map observations = (Map) subject.get("observations");
        assertEquals(observations.get("Numeric"), 10);
        assertEquals(observations.get("Text"), "Hello world");
        assertEquals(observations.get("Single Coded"), "singleCoded1");

        Object[] multiCodedAnswers = (Object[]) observations.get("Multi Coded");
        assertThat(multiCodedAnswers).hasSize(2);
        assertThat(multiCodedAnswers).contains("multiCoded1", "multiCoded2");

        Map<String, String> location = (Map<String, String>) observations.get("Location");
        assertThat(location.get(catchmentData.getAddressLevel1().getTypeString())).isEqualTo(catchmentData.getAddressLevel1().getTitle());

        assertThat(observations.get("Date")).isEqualTo("2000-11-01");
    }

    @Test
    public void shouldGetDateTimeForDateStyleObservationsForOldVersions() {
        ApiRequestContextHolder.create(new ApiRequestContext("1"));

        ResponsePage subjects = subjectApiController.getSubjects(DateTime.now().minusDays(1),
                DateTime.now().plusDays(1),
                subjectType.getName(),
                String.format("{\"%s\": \"%s\"}", singleCodedConcept.getName(), singleCodedConcept.getAnswerConcept("singleCoded1").getUuid()),
                Collections.singletonList(catchmentData.getAddressLevel1().getUuid()),
                true,
                PageRequest.of(0, 10));
        assertEquals(1, subjects.getContent().size());

        Map subject = (Map) subjects.getContent().get(0);
        Map observations = (Map) subject.get("observations");

        assertThat(observations.get("Date")).isEqualTo("2000-10-31T18:30:00.000+00:00");
    }

    @Test
    public void getSubjectWithoutLastModifiedDateTime() {
        ResponsePage subjects = subjectApiController.getSubjects(null, null,
                subjectType.getName(),
                String.format("{\"%s\": \"%s\"}", singleCodedConcept.getName(), singleCodedConcept.getAnswerConcept("singleCoded1").getUuid()),
                Collections.singletonList(catchmentData.getAddressLevel1().getUuid()),
                true,
                PageRequest.of(0, 10));
        assertEquals(1, subjects.getContent().size());
    }
}
