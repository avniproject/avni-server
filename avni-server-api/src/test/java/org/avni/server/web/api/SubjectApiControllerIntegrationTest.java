package org.avni.server.web.api;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.txData.ObservationCollectionBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.*;
import org.avni.server.web.response.ResponsePage;
import org.avni.server.web.response.SubjectResponse;
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
    @Autowired
    private TestLocationService testLocationService;
    @PersistenceContext
    private EntityManager entityManager;
    private SubjectType subjectType;
    private TestDataSetupService.TestCatchmentData catchmentData;
    private Concept singleCodedConcept;
    private Concept multiCodedConcept;
    private Concept numericConcept;
    private Concept textConcept;
    private Concept locationConcept;
    private Concept dateConcept;
    private Individual savedSubject;

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
        savedSubject = new SubjectBuilder()
                .withMandatoryFieldsForNewEntity()
                .withSubjectType(subjectType)
                .withLocation(catchmentData.getAddressLevel1())
                .withObservations(observationCollection)
                .build();
        testSubjectService.save(savedSubject);
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

    @Test
    public void getSubjectByIdShouldIncludeLocationPropertiesOfSubjectLocation() {
        addLocationPropertiesToSubjectLocation();

        SubjectResponse response = subjectApiController.get(savedSubject.getUuid(), false).getBody();

        Map locationProperties = (Map) response.get("Location Properties");
        assertEquals(2, locationProperties.size());
        assertEquals("411001", locationProperties.get("Text"));
        assertEquals("singleCoded1", locationProperties.get("Single Coded"));
    }

    @Test
    public void getSubjectByIdShouldLeaveOutLocationPropertiesWhenLocationHasNone() {
        SubjectResponse response = subjectApiController.get(savedSubject.getUuid(), false).getBody();

        assertThat(response).doesNotContainKey("Location Properties");
        assertThat(response).containsKey("Location ID");
    }

    @Test
    public void getSubjectsShouldNotIncludeLocationProperties() {
        addLocationPropertiesToSubjectLocation();

        ResponsePage subjects = subjectApiController.getSubjects(null, null, subjectType.getName(), null,
                Collections.singletonList(catchmentData.getAddressLevel1().getUuid()), false, PageRequest.of(0, 10));

        assertEquals(1, subjects.getContent().size());
        assertThat((Map) subjects.getContent().get(0)).doesNotContainKey("Location Properties");
    }

    private void addLocationPropertiesToSubjectLocation() {
        AddressLevel location = catchmentData.getAddressLevel1();
        location.setLocationProperties(new ObservationCollectionBuilder()
                .addObservation(textConcept, "411001")
                .addObservation(singleCodedConcept, singleCodedConcept.getAnswerConcept("singleCoded1"))
                .build());
        testLocationService.save(location);
        entityManager.flush();
        entityManager.clear();
    }
}
