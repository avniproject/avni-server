package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.web.request.ConceptContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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


    @Test
    @Transactional
    public void shouldSaveOrUpdateConcepts_WithNoAnswers() {
        testDataSetupService.setupOrganisation();
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        ConceptContract conceptContract1 = new ConceptContract();
        conceptContract1.setUuid(uuid1);
        conceptContract1.setName("Test Concept 1");
        conceptContract1.setDataType("Numeric");

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract1);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

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
    public void shouldSaveOrUpdateConcepts_WithAnswers() {
        testDataSetupService.setupOrganisation();
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String answerUuid1 = UUID.randomUUID().toString();
        String answerUuid2 = UUID.randomUUID().toString();

        ConceptContract answerContract1 = new ConceptContract();
        answerContract1.setUuid(answerUuid1);
        answerContract1.setName("Answer 1");
        answerContract1.setDataType("Text");

        ConceptContract answerContract2 = new ConceptContract();
        answerContract2.setUuid(answerUuid2);
        answerContract2.setName("Answer 2");
        answerContract2.setDataType("Text");

        List<ConceptContract> answers = Arrays.asList(answerContract1, answerContract2);

        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid(uuid1);
        conceptContract.setName("Test Coded Concept");
        conceptContract.setDataType("Coded");
        conceptContract.setAnswers(answers);

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(1, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));

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
                .collect(Collectors.toList());

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
    public void shouldSaveOrUpdateConcepts_WithMultipleConcepts() {
        testDataSetupService.setupOrganisation();
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
        conceptContract1.setAnswers(Arrays.asList(answerContract));

        ConceptContract conceptContract2 = new ConceptContract();
        conceptContract2.setUuid(uuid2);
        conceptContract2.setName("Second Numeric Concept");
        conceptContract2.setDataType("Numeric");

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract1, conceptContract2);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(2, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));
        assertEquals(uuid2, savedUuids.get(1));

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
    public void shouldSaveOrUpdateConcepts_WithUpdatingExistingConcepts() {
        testDataSetupService.setupOrganisation();
        // Arrange - First save
        String uuid = UUID.randomUUID().toString();

        ConceptContract initialConcept = new ConceptContract();
        initialConcept.setUuid(uuid);
        initialConcept.setName("Initial Name");
        initialConcept.setDataType("Text");

        List<ConceptContract> firstSave = Arrays.asList(initialConcept);
        conceptService.saveOrUpdateConcepts(firstSave);

        // Arrange - Now update
        ConceptContract updatedConcept = new ConceptContract();
        updatedConcept.setUuid(uuid); // Same UUID
        updatedConcept.setName("Updated Name");
        updatedConcept.setDataType("Text");

        String answerUuid = UUID.randomUUID().toString();
        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(answerUuid);
        answerContract.setName("New Answer");
        answerContract.setDataType("Text");

        updatedConcept.setAnswers(Arrays.asList(answerContract));

        // Act - Update the concept
        List<String> updatedUuids = conceptService.saveOrUpdateConcepts(Arrays.asList(updatedConcept));

        // Assert
        assertEquals(1, updatedUuids.size());
        assertEquals(uuid, updatedUuids.get(0));

        Concept updatedConceptEntity = conceptRepository.findByUuid(uuid);
        assertNotNull(updatedConceptEntity);
        assertEquals("Updated Name", updatedConceptEntity.getName()); // Name should be updated

        // Even though we passed an answer, the dataType doesn't change
        assertEquals(ConceptDataType.Text.toString(), updatedConceptEntity.getDataType());
    }
}
