package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.ConceptContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ConceptServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private ConceptService conceptService;

    @Autowired
    private ConceptRepository conceptRepository;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        testDataSetupService.setupOrganisation();
    }

    @Test
    @Transactional
    public void shouldCreateConcepts_WithNoAnswers() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        ConceptContract conceptContract1 = new ConceptContract();
        conceptContract1.setUuid(uuid1);
        conceptContract1.setName("Test Concept 1");
        conceptContract1.setDataType("Numeric");

        List<ConceptContract> conceptRequests = List.of(conceptContract1);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Full);

        // Assert
        assertEquals(1, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));

        Concept savedConcept = conceptRepository.findByUuid(uuid1);
        assertNotNull(savedConcept);
        assertEquals("Test Concept 1", savedConcept.getName());
        assertEquals(ConceptDataType.Numeric.toString(), savedConcept.getDataType());
    }

    @Test
    @Transactional
    public void shouldCreateConcepts_WithAnswers() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String answerUuid1 = UUID.randomUUID().toString();
        String answerUuid2 = UUID.randomUUID().toString();

        ConceptContract answerContract1 = new ConceptContract();
        answerContract1.setUuid(answerUuid1);
        answerContract1.setName("Answer 1");
        answerContract1.setDataType(ConceptDataType.NA.name());

        ConceptContract answerContract2 = new ConceptContract();
        answerContract2.setUuid(answerUuid2);
        answerContract2.setName("Answer 2");
        answerContract2.setDataType(ConceptDataType.NA.name());

        List<ConceptContract> answers = Arrays.asList(answerContract1, answerContract2);

        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid(uuid1);
        conceptContract.setName("Test Coded Concept");
        conceptContract.setDataType("Coded");
        conceptContract.setAnswers(answers);

        List<ConceptContract> conceptRequests = List.of(conceptContract);

        // Act
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Full);

        // Assert
        Concept savedConcept = conceptRepository.findByUuid(uuid1);
        assertNotNull(savedConcept);
        assertEquals("Test Coded Concept", savedConcept.getName());
        assertEquals(ConceptDataType.Coded.toString(), savedConcept.getDataType());

        // Verify the answers were saved correctly
        Concept updatedConcept = conceptRepository.findByUuid(savedConcept.getUuid());
        Set<ConceptAnswer> conceptAnswers = updatedConcept.getConceptAnswers();
        assertEquals(2, conceptAnswers.size());

        List<String> answerUuids = conceptAnswers.stream()
                .map(ca -> ca.getAnswerConcept().getUuid())
                .toList();

        assertTrue(answerUuids.contains(answerUuid1));
        assertTrue(answerUuids.contains(answerUuid2));

        // Verify answer concepts were saved
        Concept savedAnswer1 = conceptRepository.findByUuid(answerUuid1);
        Concept savedAnswer2 = conceptRepository.findByUuid(answerUuid2);

        assertNotNull(savedAnswer1);
        assertNotNull(savedAnswer2);
        assertEquals("Answer 1", savedAnswer1.getName());
        assertEquals("Answer 2", savedAnswer2.getName());
    }

    @Test
    @Transactional
    public void shouldCreateMultipleConcepts_WithAnswers() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String answerUuid = UUID.randomUUID().toString();

        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(answerUuid);
        answerContract.setName("Common Answer");
        answerContract.setDataType("Text");

        ConceptContract conceptContract1 = new ConceptContract();
        conceptContract1.setUuid(uuid1);
        conceptContract1.setName("First Coded Concept");
        conceptContract1.setDataType("Coded");
        conceptContract1.setAnswers(List.of(answerContract));

        ConceptContract conceptContract2 = new ConceptContract();
        conceptContract2.setUuid(uuid2);
        conceptContract2.setName("Second Numeric Concept");
        conceptContract2.setDataType("Numeric");

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract1, conceptContract2);

        // Act
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Full);

        // Assert
        Concept savedConcept1 = conceptRepository.findByUuid(uuid1);
        Concept savedConcept2 = conceptRepository.findByUuid(uuid2);

        assertNotNull(savedConcept1);
        assertNotNull(savedConcept2);

        assertEquals("First Coded Concept", savedConcept1.getName());
        assertEquals("Second Numeric Concept", savedConcept2.getName());

        assertEquals(ConceptDataType.Coded.toString(), savedConcept1.getDataType());
        assertEquals(ConceptDataType.Numeric.toString(), savedConcept2.getDataType());

        // Verify the answer was associated with first concept
        Concept updatedConcept1 = conceptRepository.findByUuid(savedConcept1.getUuid());
        Set<ConceptAnswer> conceptAnswers1 = updatedConcept1.getConceptAnswers();
        assertEquals(1, conceptAnswers1.size());
        assertEquals(answerUuid, conceptAnswers1.stream().findFirst().get().getAnswerConcept().getUuid());
    }

    @Test
    @Transactional
    public void shouldUpdateConcepts() {
        // Arrange - First save
        String uuid = UUID.randomUUID().toString();

        ConceptContract initialConcept = new ConceptContract();
        initialConcept.setUuid(uuid);
        initialConcept.setName("Initial Name");
        initialConcept.setDataType("Coded");

        conceptService.saveOrUpdateConcepts(List.of(initialConcept), ConceptContract.RequestType.Full);

        // Arrange - Now to update
        ConceptContract updatedConcept = new ConceptContract();
        updatedConcept.setUuid(uuid); // Same UUID
        updatedConcept.setName("Updated Name");
        updatedConcept.setDataType("Coded");

        String answerUuid = UUID.randomUUID().toString();
        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(answerUuid);
        answerContract.setName("New Answer");
        answerContract.setDataType("NA");

        updatedConcept.setAnswers(List.of(answerContract));

        // Act
        conceptService.saveOrUpdateConcepts(List.of(updatedConcept), ConceptContract.RequestType.Full);

        // Assert
        Concept updatedConceptEntity = conceptRepository.findByUuid(uuid);
        assertNotNull(updatedConceptEntity);
        assertEquals("Updated Name", updatedConceptEntity.getName()); // Name should be updated

        // Even though we passed an answer, the dataType doesn't change
        assertEquals(ConceptDataType.Coded.name(), updatedConceptEntity.getDataType());
        assertEquals("New Answer", updatedConceptEntity.getConceptAnswers().stream()
                .findFirst().get().getAnswerConcept().getName()); // Check the answer was added
    }

    @Test
    public void editCodedConcept() {
        // Arrange
        String uuid = UUID.randomUUID().toString();
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid(uuid);
        conceptContract.setName("Test Coded Concept");
        conceptContract.setDataType("Coded");

        String firstAnswerUUID = UUID.randomUUID().toString();
        ConceptContract firstAnswer = new ConceptContract();
        firstAnswer.setUuid(firstAnswerUUID);
        firstAnswer.setName("Answer 1");
        firstAnswer.setDataType("NA");
        firstAnswer.setUnique(true);
        firstAnswer.setMediaUrl("foo");

        String secondAnswerUUID = UUID.randomUUID().toString();
        ConceptContract secondAnswer = new ConceptContract();
        secondAnswer.setUuid(secondAnswerUUID);
        secondAnswer.setName("Answer 2");
        secondAnswer.setDataType("NA");
        firstAnswer.setAbnormal(true);

        conceptContract.setAnswers(List.of(firstAnswer, secondAnswer));
        conceptService.saveOrUpdateConcepts(List.of(conceptContract), ConceptContract.RequestType.Full);

        // Act
        firstAnswer.setUnique(false);
        firstAnswer.setAbnormal(true);
        secondAnswer.setUnique(true);
        secondAnswer.setAbnormal(false);
        secondAnswer.setMediaUrl("bar");

        conceptService.saveOrUpdateConcepts(List.of(conceptContract), ConceptContract.RequestType.Full);

        // Assert
        Concept concept = conceptRepository.findByUuid(uuid);

        assertFalse(concept.getConceptAnswer(firstAnswerUUID).isUnique());
        assertTrue(concept.getConceptAnswer(firstAnswerUUID).isAbnormal());
        assertTrue(concept.getConceptAnswer(secondAnswerUUID).isUnique());
        assertFalse(concept.getConceptAnswer(secondAnswerUUID).isAbnormal());
        assertEquals("foo", concept.getAnswerConcept("Answer 1").getMediaUrl());
        assertEquals("bar", concept.getAnswerConcept("Answer 2").getMediaUrl());
    }

    @Test
    public void shouldThrowErrorWhenCreatingConceptWithSameNameButDifferentUUID() {
        // Arrange
        // First create a concept
        String firstConceptUuid = UUID.randomUUID().toString();
        ConceptContract firstConceptContract = new ConceptContract();
        firstConceptContract.setUuid(firstConceptUuid);
        firstConceptContract.setName("Duplicate Name Concept");
        firstConceptContract.setDataType("Numeric");

        conceptService.saveOrUpdateConcepts(List.of(firstConceptContract), ConceptContract.RequestType.Full);

        // Verify first concept is saved correctly
        Concept savedFirstConcept = conceptRepository.findByUuid(firstConceptUuid);
        assertNotNull(savedFirstConcept);
        assertEquals("Duplicate Name Concept", savedFirstConcept.getName());

        // Now try to create a second concept with the same name but different UUID
        String secondConceptUuid = UUID.randomUUID().toString();
        ConceptContract secondConceptContract = new ConceptContract();
        secondConceptContract.setUuid(secondConceptUuid);
        secondConceptContract.setName("Duplicate Name Concept"); // Same name as first concept
        secondConceptContract.setDataType("Text"); // Different data type

        // Assert that it throws BadRequestError with appropriate message
        BadRequestError error = assertThrows(BadRequestError.class, () ->
                conceptService.saveOrUpdateConcepts(List.of(secondConceptContract), ConceptContract.RequestType.Full)
        );

        // Verify error message contains useful information
        String errorMessage = error.getMessage();
        assertTrue(errorMessage.contains("Duplicate Name Concept"));
        assertTrue(errorMessage.contains("already exists with different UUID"));
        assertTrue(errorMessage.contains(firstConceptUuid));
    }

    @Test
    public void shouldThrowErrorWhenCreatingAnswerConceptWithSameNameButDifferentUUID() {
        // Arrange
        // First create a standalone concept that will be used later as an answer
        String standaloneConceptUuid = UUID.randomUUID().toString();
        ConceptContract standaloneConceptContract = new ConceptContract();
        standaloneConceptContract.setUuid(standaloneConceptUuid);
        standaloneConceptContract.setName("Duplicate Answer Name");
        standaloneConceptContract.setDataType("NA");

        conceptService.saveOrUpdateConcepts(List.of(standaloneConceptContract), ConceptContract.RequestType.Full);

        // Now create a parent concept with a different answer concept that has the same name
        String parentConceptUuid = UUID.randomUUID().toString();
        String newAnswerUuid = UUID.randomUUID().toString(); // Different from standaloneConceptUuid

        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(newAnswerUuid);
        answerContract.setName("Duplicate Answer Name"); // Same name as standalone concept
        answerContract.setDataType("NA");

        ConceptContract parentConceptContract = new ConceptContract();
        parentConceptContract.setUuid(parentConceptUuid);
        parentConceptContract.setName("Parent Coded Concept");
        parentConceptContract.setDataType("Coded");
        parentConceptContract.setAnswers(List.of(answerContract));

        // Assert that it throws BadRequestError with appropriate message
        BadRequestError error = assertThrows(BadRequestError.class, () ->
                conceptService.saveOrUpdateConcepts(List.of(parentConceptContract), ConceptContract.RequestType.Full)
        );

        // Verify error message contains useful information
        String errorMessage = error.getMessage();
        assertTrue(errorMessage.contains("Duplicate Answer Name"));
        assertTrue(errorMessage.contains("already exists with different UUID"));
        assertTrue(errorMessage.contains(standaloneConceptUuid));
    }

    @Test
    public void bundleUploadUpdateCodedConcept() {
        // Arrange
        String uuid = UUID.randomUUID().toString();
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid(uuid);
        conceptContract.setName("Test Coded Concept");
        conceptContract.setDataType("Coded");

        String firstAnswerUUID = UUID.randomUUID().toString();
        ConceptContract firstAnswer = new ConceptContract();
        firstAnswer.setUuid(firstAnswerUUID);
        firstAnswer.setName("Answer 1");
        firstAnswer.setDataType("NA");
        firstAnswer.setUnique(true);
        firstAnswer.setAbnormal(false);
        firstAnswer.setMediaUrl("foo");

        String secondAnswerUUID = UUID.randomUUID().toString();
        ConceptContract secondAnswer = new ConceptContract();
        secondAnswer.setUuid(secondAnswerUUID);
        secondAnswer.setName("Answer 2");
        secondAnswer.setDataType("NA");
        secondAnswer.setUnique(false);
        secondAnswer.setAbnormal(true);

        conceptContract.setAnswers(List.of(firstAnswer, secondAnswer));
        conceptService.saveOrUpdateConcepts(List.of(conceptContract), ConceptContract.RequestType.Full);

        // Act
        conceptContract = new ConceptContract();
        conceptContract.setUuid(uuid);
        conceptContract.setName("Test Coded Concept New");
        conceptContract.setDataType("Coded");

        firstAnswer = new ConceptContract();
        firstAnswer.setUuid(firstAnswerUUID);
        firstAnswer.setName("Answer 1");
        secondAnswer = new ConceptContract();
        secondAnswer.setUuid(secondAnswerUUID);
        secondAnswer.setName("Answer 2");

        conceptContract.setAnswers(List.of(firstAnswer, secondAnswer));

        conceptService.saveOrUpdateConcepts(List.of(conceptContract), ConceptContract.RequestType.Bundle);

        // Assert
        Concept concept = conceptRepository.findByUuid(uuid);

        assertTrue(concept.getConceptAnswer(firstAnswerUUID).isUnique());
        assertFalse(concept.getConceptAnswer(firstAnswerUUID).isAbnormal());
        assertFalse(concept.getConceptAnswer(secondAnswerUUID).isUnique());
        assertTrue(concept.getConceptAnswer(secondAnswerUUID).isAbnormal());
        assertEquals("foo", concept.getAnswerConcept("Answer 1").getMediaUrl());
    }
}
