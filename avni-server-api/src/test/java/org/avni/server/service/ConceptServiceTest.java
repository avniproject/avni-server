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
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

        Concept savedConcept = new Concept();
        savedConcept.setUuid(uuid1);

        when(conceptRepository.save(any(Concept.class))).thenReturn(savedConcept);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(1, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));

        // Verify the saveOrUpdate method was called correctly
        // First pass with false, false
        verify(conceptRepository, times(2)).save(any(Concept.class));
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

        Concept savedConcept = new Concept();
        savedConcept.setUuid(uuid1);

        Concept savedAnswerConcept = new Concept();
        savedAnswerConcept.setUuid(answerUuid1);

        when(conceptRepository.save(any(Concept.class)))
                .thenReturn(savedConcept)
                .thenReturn(savedAnswerConcept)
                .thenReturn(savedConcept);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(1, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));

        // Verify repository interactions
        // First pass (main concept), second pass (answer concept), third pass (main concept with answers)
        verify(conceptRepository, times(3)).save(any(Concept.class));
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
        existingAnswerConcept.setName("Existing Answer"); // Note: different name than in the contract

        // Create the main concept
        Concept mainConcept = new Concept();
        mainConcept.setUuid(uuid1);

        // Create ConceptService spy
        ConceptService spyService = Mockito.spy(conceptService);

        // Mock repository behavior for finding existing answer concept
        when(conceptRepository.findByUuid(answerUuid1)).thenReturn(existingAnswerConcept);

        // Mock saveOrUpdate to properly simulate behavior with skipUpdateIfPresent=true
        doReturn(mainConcept).when(spyService).saveOrUpdate(eq(conceptContract), eq(false), eq(false));
        doReturn(existingAnswerConcept).when(spyService).saveOrUpdate(eq(answerContract), eq(false), eq(true)); // Important: return existing concept
        doReturn(mainConcept).when(spyService).saveOrUpdate(eq(conceptContract), eq(true), eq(false));

        // Act
        List<String> savedUuids = spyService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(1, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));

        // Verify the saveOrUpdate method was called with specific parameters for each phase
        // First pass: save main concept without answers
        verify(spyService).saveOrUpdate(eq(conceptContract), eq(false), eq(false));

        // Second pass: save answer concept WITH skipUpdateIfPresent=true
        verify(spyService).saveOrUpdate(eq(answerContract), eq(false), eq(true));

        // Third pass: save main concept with createAnswers=true
        verify(spyService).saveOrUpdate(eq(conceptContract), eq(true), eq(false));

        // The most important verification: for the answer concept, the saveOrUpdate method
        // should have been called with skipUpdateIfPresent=true
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

        Concept savedConcept1 = Mockito.spy(new Concept());
        savedConcept1.setUuid(uuid1);

        Concept savedConcept2 = Mockito.spy(new Concept());
        savedConcept2.setUuid(uuid2);

        Concept savedAnswerConcept = Mockito.spy(new Concept());
        savedAnswerConcept.setUuid(answerUuid);

        when(conceptRepository.save(any(Concept.class)))
                .thenReturn(savedConcept1)
                .thenReturn(savedConcept2)
                .thenReturn(savedAnswerConcept)
                .thenReturn(savedConcept1)
                .thenReturn(savedConcept2);

        // Act
        List<String> savedUuids = conceptService.saveOrUpdateConcepts(conceptRequests);

        // Assert
        assertEquals(2, savedUuids.size());
        assertEquals(uuid1, savedUuids.get(0));
        assertEquals(uuid2, savedUuids.get(1));

        // Verify repository interactions
        // First pass (2 concepts), second pass (1 answer concept), third pass (2 concepts with answers)
        verify(conceptRepository, times(5)).save(any(Concept.class));

        // Verify 3-phase saving pattern - first without answers, then answers, then concepts with answers linked
        // We need to use a special ConceptService spy to track this properly
        ConceptService spyService = Mockito.spy(conceptService);
        doReturn(savedConcept1).doReturn(savedConcept2).doReturn(savedAnswerConcept).doReturn(savedConcept1).doReturn(savedConcept2)
                .when(spyService).saveOrUpdate(any(ConceptContract.class), anyBoolean(), anyBoolean());

        spyService.saveOrUpdateConcepts(conceptRequests);

        // First pass: main concepts without answers
        verify(spyService, times(2)).saveOrUpdate(any(ConceptContract.class), eq(false), eq(false));
        // Second pass: answer concepts 
        verify(spyService, times(1)).saveOrUpdate(any(ConceptContract.class), eq(false), eq(true));
        // Third pass: main concepts with answers
        verify(spyService, times(2)).saveOrUpdate(any(ConceptContract.class), eq(true), eq(false));
    }
}
