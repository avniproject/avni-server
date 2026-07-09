package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Functional tests for bulk subject migration JSON serialization.
 * Tests the core functionality that was causing Spring Batch converter errors.
 */
public class SubjectMigrationServiceTest {
    private static final String SYNC_CONCEPT_1_UUID = "sync-concept-1-uuid";

    @Mock
    private EntityApprovalStatusRepository entityApprovalStatusRepository;
    @Mock
    private SubjectMigrationRepository subjectMigrationRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private ProgramEnrolmentRepository programEnrolmentRepository;
    @Mock
    private ProgramEncounterRepository programEncounterRepository;
    @Mock
    private GroupSubjectRepository groupSubjectRepository;
    @Mock
    private AddressLevelService addressLevelService;
    @Mock
    private ChecklistRepository checklistRepository;
    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private IndividualRelationshipRepository individualRelationshipRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private IndividualService individualService;
    @Mock
    private AvniJobRepository avniJobRepository;

    private SubjectMigrationService subjectMigrationService;

    @Before
    public void setUp() {
        initMocks(this);
        subjectMigrationService = new SubjectMigrationService(entityApprovalStatusRepository, subjectMigrationRepository,
                subjectTypeRepository, individualRepository, encounterRepository, programEnrolmentRepository,
                programEncounterRepository, groupSubjectRepository, addressLevelService, checklistRepository,
                checklistItemRepository, individualRelationshipRepository, accessControlService, locationRepository,
                conceptRepository, individualService, avniJobRepository);

        Concept textConcept = new Concept();
        textConcept.setUuid(SYNC_CONCEPT_1_UUID);
        textConcept.setDataType(ConceptDataType.Text.toString());
        when(conceptRepository.findByUuid(SYNC_CONCEPT_1_UUID)).thenReturn(textConcept);
    }

    @Test
    public void jsonSerialization_shouldSerializeAndDeserializeBulkRequestWithAddresses() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(1L, 2L, 3L));
        
        Map<String, String> destinationAddresses = new HashMap<>();
        destinationAddresses.put("1", "address-1");
        destinationAddresses.put("2", "address-2");
        request.setDestinationAddresses(destinationAddresses);
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("subjectIds"));
        assertTrue(json.contains("destinationAddresses"));
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertEquals(request.getSubjectIds(), deserialized.getSubjectIds());
        assertEquals(request.getDestinationAddresses(), deserialized.getDestinationAddresses());
    }

    @Test
    public void jsonSerialization_shouldSerializeAndDeserializeBulkRequestWithSyncConcepts() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(4L, 5L));
        
        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put("concept-1", "value-1");
        destinationSyncConcepts.put("concept-2", "value-2");
        request.setDestinationSyncConcepts(destinationSyncConcepts);
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("subjectIds"));
        assertTrue(json.contains("destinationSyncConcepts"));
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertEquals(request.getSubjectIds(), deserialized.getSubjectIds());
        assertEquals(request.getDestinationSyncConcepts(), deserialized.getDestinationSyncConcepts());
    }

    @Test
    public void jsonSerialization_shouldHandleEmptyCollections() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList());
        request.setDestinationAddresses(new HashMap<>());
        request.setDestinationSyncConcepts(new HashMap<>());
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertNotNull(deserialized.getSubjectIds());
        assertTrue(deserialized.getSubjectIds().isEmpty());
    }

    @Test
    public void jsonSerialization_shouldHandleNullValues() throws Exception {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(1L));
        // Leave other fields null
        
        String json = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        assertNotNull(json);
        
        BulkSubjectMigrationRequest deserialized = ObjectMapperSingleton.getObjectMapper()
                .readValue(json, BulkSubjectMigrationRequest.class);
        
        assertEquals(request.getSubjectIds(), deserialized.getSubjectIds());
    }

    @Test
    public void bulkMigrateBySyncConcept_shouldMigrateToNewValue() {
        Individual subject = buildSubjectWithSyncConcept1Value("Samta Vikas Sansthan");
        when(individualRepository.findOne(1L)).thenReturn(subject);
        when(individualRepository.findByUuid(subject.getUuid())).thenReturn(subject);

        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(SYNC_CONCEPT_1_UUID, "Kalinga Development Foundation");
        Map<String, String> migrationFailures = subjectMigrationService.bulkMigrateBySyncConcept(Arrays.asList(1L), destinationSyncConcepts);

        assertTrue(migrationFailures.isEmpty());
        ArgumentCaptor<SubjectMigration> subjectMigrationCaptor = ArgumentCaptor.forClass(SubjectMigration.class);
        verify(subjectMigrationRepository).save(subjectMigrationCaptor.capture());
        assertEquals("Samta Vikas Sansthan", subjectMigrationCaptor.getValue().getOldSyncConcept1Value());
        assertEquals("Kalinga Development Foundation", subjectMigrationCaptor.getValue().getNewSyncConcept1Value());
        verify(individualService).save(subject);
        assertEquals("Kalinga Development Foundation", subject.getObservations().getObjectAsSingleStringValue(SYNC_CONCEPT_1_UUID));
    }

    @Test
    public void bulkMigrateBySyncConcept_shouldMigrateWhenValuesDifferOnlyByWhitespace() {
        Individual subject = buildSubjectWithSyncConcept1Value(" Samta Vikas Sansthan");
        when(individualRepository.findOne(1L)).thenReturn(subject);
        when(individualRepository.findByUuid(subject.getUuid())).thenReturn(subject);

        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(SYNC_CONCEPT_1_UUID, "Samta Vikas Sansthan");
        Map<String, String> migrationFailures = subjectMigrationService.bulkMigrateBySyncConcept(Arrays.asList(1L), destinationSyncConcepts);

        assertTrue(migrationFailures.isEmpty());
        ArgumentCaptor<SubjectMigration> subjectMigrationCaptor = ArgumentCaptor.forClass(SubjectMigration.class);
        verify(subjectMigrationRepository).save(subjectMigrationCaptor.capture());
        assertEquals(" Samta Vikas Sansthan", subjectMigrationCaptor.getValue().getOldSyncConcept1Value());
        assertEquals("Samta Vikas Sansthan", subjectMigrationCaptor.getValue().getNewSyncConcept1Value());
        verify(individualService).save(subject);
        assertEquals("Samta Vikas Sansthan", subject.getObservations().getObjectAsSingleStringValue(SYNC_CONCEPT_1_UUID));
    }

    @Test
    public void bulkMigrateBySyncConcept_shouldStoreDestinationValueVerbatim() {
        Individual subject = buildSubjectWithSyncConcept1Value("Samta Vikas Sansthan");
        when(individualRepository.findOne(1L)).thenReturn(subject);
        when(individualRepository.findByUuid(subject.getUuid())).thenReturn(subject);

        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(SYNC_CONCEPT_1_UUID, " Samta Vikas Sansthan ");
        Map<String, String> migrationFailures = subjectMigrationService.bulkMigrateBySyncConcept(Arrays.asList(1L), destinationSyncConcepts);

        assertTrue(migrationFailures.isEmpty());
        ArgumentCaptor<SubjectMigration> subjectMigrationCaptor = ArgumentCaptor.forClass(SubjectMigration.class);
        verify(subjectMigrationRepository).save(subjectMigrationCaptor.capture());
        assertEquals("Samta Vikas Sansthan", subjectMigrationCaptor.getValue().getOldSyncConcept1Value());
        assertEquals(" Samta Vikas Sansthan ", subjectMigrationCaptor.getValue().getNewSyncConcept1Value());
        verify(individualService).save(subject);
        assertEquals(" Samta Vikas Sansthan ", subject.getObservations().getObjectAsSingleStringValue(SYNC_CONCEPT_1_UUID));
    }

    @Test
    public void bulkMigrateBySyncConcept_shouldFailWhenSourceAndDestinationValuesAreSame() {
        Individual subject = buildSubjectWithSyncConcept1Value("Samta Vikas Sansthan");
        when(individualRepository.findOne(1L)).thenReturn(subject);

        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(SYNC_CONCEPT_1_UUID, "Samta Vikas Sansthan");
        Map<String, String> migrationFailures = subjectMigrationService.bulkMigrateBySyncConcept(Arrays.asList(1L), destinationSyncConcepts);

        assertEquals(1, migrationFailures.size());
        assertEquals("Source value and Destination value are the same", migrationFailures.get("1"));
        verify(individualService, never()).save(any(Individual.class));
        verify(subjectMigrationRepository, never()).save(any(SubjectMigration.class));
    }

    @Test
    public void bulkMigrateBySyncConcept_shouldMigrateWhenCurrentValueIsNull() {
        Individual subject = buildSubjectWithSyncConcept1Value(null);
        when(individualRepository.findOne(1L)).thenReturn(subject);
        when(individualRepository.findByUuid(subject.getUuid())).thenReturn(subject);

        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put(SYNC_CONCEPT_1_UUID, "Samta Vikas Sansthan");
        Map<String, String> migrationFailures = subjectMigrationService.bulkMigrateBySyncConcept(Arrays.asList(1L), destinationSyncConcepts);

        assertTrue(migrationFailures.isEmpty());
        ArgumentCaptor<SubjectMigration> subjectMigrationCaptor = ArgumentCaptor.forClass(SubjectMigration.class);
        verify(subjectMigrationRepository).save(subjectMigrationCaptor.capture());
        assertNull(subjectMigrationCaptor.getValue().getOldSyncConcept1Value());
        assertEquals("Samta Vikas Sansthan", subjectMigrationCaptor.getValue().getNewSyncConcept1Value());
        verify(individualService).save(subject);
    }

    private Individual buildSubjectWithSyncConcept1Value(String syncConcept1Value) {
        SubjectType subjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setSyncRegistrationConcept1(SYNC_CONCEPT_1_UUID)
                .build();
        AddressLevel addressLevel = new AddressLevel();
        addressLevel.setId(1L);
        Individual subject = new Individual();
        subject.assignUUID();
        subject.setSubjectType(subjectType);
        subject.setAddressLevel(addressLevel);
        ObservationCollection observations = new ObservationCollection();
        if (syncConcept1Value != null) {
            observations.put(SYNC_CONCEPT_1_UUID, syncConcept1Value);
        }
        subject.setObservations(observations);
        subject.setSyncConcept1Value(syncConcept1Value);
        return subject;
    }
}
