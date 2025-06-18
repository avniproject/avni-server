package org.avni.server.service;

import org.avni.server.dao.AnswerConceptMigrationRepository;
import org.avni.server.dao.ConceptAnswerRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.domain.Concept;
import org.avni.server.web.request.ConceptContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class ConceptServiceTest {

    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private ConceptAnswerRepository conceptAnswerRepository;
    @Mock
    private FormElementRepository formElementRepository;
    @Mock
    private AnswerConceptMigrationRepository answerConceptMigrationRepository;
    @Mock
    private LocationRepository locationRepository;

    private ConceptService conceptService;

    @Before
    public void setUp() {
        initMocks(this);
        conceptService = new ConceptService(
                conceptRepository,
                conceptAnswerRepository,
                formElementRepository,
                answerConceptMigrationRepository,
                locationRepository
        );
    }

    @Test
    public void shouldSaveOrUpdateConcepts_WithNoAnswers() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        ConceptContract conceptContract1 = new ConceptContract();
        conceptContract1.setUuid(uuid1);
        conceptContract1.setName("Concept 1");
        conceptContract1.setDataType("Numeric");

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract1);

        // Mock repository behavior
        when(conceptRepository.findByUuid(uuid1)).thenReturn(null);
        when(conceptRepository.findByName(anyString())).thenReturn(null);
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(1, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));

        // Verify that the concept was saved with the correct name
        ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository, times(2)).save(conceptCaptor.capture());

        List<Concept> savedConcepts = conceptCaptor.getAllValues();
        // There should be two saves for the same concept (first pass without answers, second with answers)
        assertEquals("Concept 1", savedConcepts.get(0).getName());
        assertEquals("Concept 1", savedConcepts.get(1).getName());
        assertEquals(uuid1, savedConcepts.get(0).getUuid());
        assertEquals(uuid1, savedConcepts.get(1).getUuid());
    }

    @Test
    public void shouldSaveOrUpdateConcepts_WithAnswers() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String answerUuid1 = UUID.randomUUID().toString();

        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(answerUuid1);
        answerContract.setName("Answer 1");
        answerContract.setDataType("Text");

        ConceptContract conceptContract1 = new ConceptContract();
        conceptContract1.setUuid(uuid1);
        conceptContract1.setName("Concept 1");
        conceptContract1.setDataType("Coded");
        conceptContract1.setAnswers(Arrays.asList(answerContract));

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract1);

        // Mock repository behavior
        when(conceptRepository.findByUuid(uuid1)).thenReturn(null);
        when(conceptRepository.findByUuid(answerUuid1)).thenReturn(null);
        when(conceptRepository.findByName(anyString())).thenReturn(null);
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(2, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(1));
        assertEquals(answerUuid1, savedUuids.get(0));

        // Verify concepts were saved with correct properties
        ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository, times(3)).save(conceptCaptor.capture());

        List<Concept> savedConcepts = conceptCaptor.getAllValues();

        // Verify first save (main concept without answers)
        assertEquals(uuid1, savedConcepts.get(0).getUuid());
        assertEquals("Concept 1", savedConcepts.get(0).getName());
        assertEquals("Coded", savedConcepts.get(0).getDataType());

        // Verify second save (answer concept)
        assertEquals(answerUuid1, savedConcepts.get(1).getUuid());
        assertEquals("Answer 1", savedConcepts.get(1).getName());
        assertEquals("Text", savedConcepts.get(1).getDataType());

        // Verify third save (main concept with answers)
        assertEquals(uuid1, savedConcepts.get(2).getUuid());
        assertEquals("Concept 1", savedConcepts.get(2).getName());
    }

    @Test
    public void shouldSaveOrUpdateConcepts_WithAnswers_WithoutUpdatingExistingAnswers() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String answerUuid1 = UUID.randomUUID().toString();

        // Set up the answer contract - this represents an existing answer concept
        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(answerUuid1);
        answerContract.setName("Answer 1");
        answerContract.setDataType("Text");

        // Main concept with the answer
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid(uuid1);
        conceptContract.setName("Concept 1");
        conceptContract.setDataType("Coded");
        conceptContract.setAnswers(Arrays.asList(answerContract));

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract);

        // Create the "existing" answer concept that should not be updated
        Concept existingAnswerConcept = new Concept();
        existingAnswerConcept.setUuid(answerUuid1);
        existingAnswerConcept.setId(2l);
        existingAnswerConcept.setName("Existing Answer"); // Note: different name than in the contract

        // Save the existing answer concept
        when(conceptRepository.findByUuid(answerUuid1)).thenReturn(existingAnswerConcept);
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conceptRepository.findByName(anyString())).thenReturn(null);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(2, savedUuids.size());
        assertEquals(answerUuid1, savedUuids.get(0));
        assertEquals(uuid1, savedUuids.get(1));

        // Verify that answer concept maintained its original name
        // This will fail if skipUpdateIfPresent functionality is commented out
        ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository, atLeastOnce()).save(conceptCaptor.capture());

        boolean foundAnswerConcept = false;
        for (Concept savedConcept : conceptCaptor.getAllValues()) {
            if (answerUuid1.equals(savedConcept.getUuid())) {
                foundAnswerConcept = true;
                assertEquals("Answer concept name should not be updated when skipUpdateIfPresent is true",
                        "Existing Answer", savedConcept.getName());
            }
        }

        assertTrue("The answer concept should have been saved", foundAnswerConcept);
    }

    @Test
    public void shouldSaveOrUpdateConcepts_WithMultipleConcepts() {
        // Arrange
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String answerUuid = UUID.randomUUID().toString();

        ConceptContract answerContract = new ConceptContract();
        answerContract.setUuid(answerUuid);
        answerContract.setName("Answer");
        answerContract.setDataType("Text");

        ConceptContract conceptContract1 = new ConceptContract();
        conceptContract1.setUuid(uuid1);
        conceptContract1.setName("Concept 1");
        conceptContract1.setDataType("Coded");
        conceptContract1.setAnswers(Arrays.asList(answerContract));

        ConceptContract conceptContract2 = new ConceptContract();
        conceptContract2.setUuid(uuid2);
        conceptContract2.setName("Concept 2");
        conceptContract2.setDataType("Numeric");

        List<ConceptContract> conceptRequests = Arrays.asList(conceptContract1, conceptContract2);

        // Mock repository behavior
        when(conceptRepository.findByUuid(uuid1)).thenReturn(null);
        when(conceptRepository.findByUuid(uuid2)).thenReturn(null);
        when(conceptRepository.findByUuid(answerUuid)).thenReturn(null);
        when(conceptRepository.findByName(anyString())).thenReturn(null);
        when(conceptRepository.save(any(Concept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(3, savedUuids.size());
        assertEquals(answerUuid, savedUuids.get(0));
        assertEquals(uuid1, savedUuids.get(1));
        assertEquals(uuid2, savedUuids.get(2));

        // Verify repository interactions
        ArgumentCaptor<Concept> conceptCaptor = ArgumentCaptor.forClass(Concept.class);
        verify(conceptRepository, times(5)).save(conceptCaptor.capture());

        List<Concept> savedConcepts = conceptCaptor.getAllValues();

        // First pass: Concept 1 and Concept 2 without answers
        assertEquals(uuid1, savedConcepts.get(0).getUuid());
        assertEquals("Concept 1", savedConcepts.get(0).getName());
        assertEquals("Coded", savedConcepts.get(0).getDataType());

        assertEquals(uuid2, savedConcepts.get(1).getUuid());
        assertEquals("Concept 2", savedConcepts.get(1).getName());
        assertEquals("Numeric", savedConcepts.get(1).getDataType());

        // Second pass: Answer concept
        assertEquals(answerUuid, savedConcepts.get(2).getUuid());
        assertEquals("Answer", savedConcepts.get(2).getName());
        assertEquals("Text", savedConcepts.get(2).getDataType());

        // Third pass: Concept 1 and Concept 2 with answers
        assertEquals(uuid1, savedConcepts.get(3).getUuid());
        assertEquals("Concept 1", savedConcepts.get(3).getName());

        assertEquals(uuid2, savedConcepts.get(4).getUuid());
        assertEquals("Concept 2", savedConcepts.get(4).getName());

        // We can verify that the 3-phase process happened correctly by the sequence of saves
        // No need for a spy ConceptService since we're testing actual behavior
    }
}
