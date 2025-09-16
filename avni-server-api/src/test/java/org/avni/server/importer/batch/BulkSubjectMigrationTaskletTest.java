package org.avni.server.importer.batch;

import org.avni.server.importer.batch.sync.attributes.bulkmigration.BulkSubjectMigrationTasklet;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.service.SubjectMigrationService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class BulkSubjectMigrationTaskletTest {

    @Mock
    private SubjectMigrationService subjectMigrationService;

    @Mock
    private BulkUploadS3Service bulkUploadS3Service;

    @Mock
    private ChunkContext chunkContext;

    @Mock
    private StepContext stepContext;

    @Mock
    private StepExecution stepExecution;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private ExecutionContext executionContext;

    private BulkSubjectMigrationTasklet tasklet;

    @Before
    public void setup() {
        openMocks(this);
        tasklet = new BulkSubjectMigrationTasklet(subjectMigrationService, bulkUploadS3Service);
        
        // Setup mock chain for ChunkContext
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        when(jobExecution.getExecutionContext()).thenReturn(executionContext);
    }

    @Test
    public void execute_shouldMigrateSubjectsByAddress() throws Exception {
        BulkSubjectMigrationRequest request = createAddressMigrationRequest();
        String requestJson = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        Map<String, String> expectedFailures = new HashMap<>();

        when(subjectMigrationService.bulkMigrate(eq(SubjectMigrationService.BulkSubjectMigrationModes.byAddress), eq(request)))
                .thenReturn(expectedFailures);

        ReflectionTestUtils.setField(tasklet, "mode", "byAddress");
        ReflectionTestUtils.setField(tasklet, "bulkSubjectMigrationParametersJson", requestJson);
        ReflectionTestUtils.setField(tasklet, "uuid", "test-uuid");

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(subjectMigrationService).bulkMigrate(eq(SubjectMigrationService.BulkSubjectMigrationModes.byAddress), any(BulkSubjectMigrationRequest.class));
    }

    @Test
    public void execute_shouldMigrateSubjectsBySyncConcept() throws Exception {
        BulkSubjectMigrationRequest request = createSyncConceptMigrationRequest();
        String requestJson = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        Map<String, String> expectedFailures = new HashMap<>();

        when(subjectMigrationService.bulkMigrate(eq(SubjectMigrationService.BulkSubjectMigrationModes.bySyncConcept), eq(request)))
                .thenReturn(expectedFailures);

        ReflectionTestUtils.setField(tasklet, "mode", "bySyncConcept");
        ReflectionTestUtils.setField(tasklet, "bulkSubjectMigrationParametersJson", requestJson);
        ReflectionTestUtils.setField(tasklet, "uuid", "test-uuid");

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(subjectMigrationService).bulkMigrate(eq(SubjectMigrationService.BulkSubjectMigrationModes.bySyncConcept), any(BulkSubjectMigrationRequest.class));
    }

    @Test
    public void execute_shouldHandleFailedMigrations() throws Exception {
        BulkSubjectMigrationRequest request = createAddressMigrationRequest();
        String requestJson = ObjectMapperSingleton.getObjectMapper().writeValueAsString(request);
        Map<String, String> expectedFailures = new HashMap<>();
        expectedFailures.put("1", "Subject not found");

        when(subjectMigrationService.bulkMigrate(eq(SubjectMigrationService.BulkSubjectMigrationModes.byAddress), any(BulkSubjectMigrationRequest.class)))
                .thenReturn(expectedFailures);

        ReflectionTestUtils.setField(tasklet, "mode", "byAddress");
        ReflectionTestUtils.setField(tasklet, "bulkSubjectMigrationParametersJson", requestJson);
        ReflectionTestUtils.setField(tasklet, "uuid", "test-uuid");

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(subjectMigrationService).bulkMigrate(any(), any());
    }

    @Test
    public void execute_shouldHandleJsonDeserializationError() throws Exception {
        ReflectionTestUtils.setField(tasklet, "mode", "byAddress");
        ReflectionTestUtils.setField(tasklet, "bulkSubjectMigrationParametersJson", "invalid-json");
        ReflectionTestUtils.setField(tasklet, "uuid", "test-uuid");

        try {
            tasklet.execute(null, null);
        } catch (RuntimeException e) {
            assertEquals("Failed to deserialize bulk subject migration parameters", e.getMessage());
        }
    }

    private BulkSubjectMigrationRequest createAddressMigrationRequest() {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(1L, 2L));

        Map<String, String> destinationAddresses = new HashMap<>();
        destinationAddresses.put("1", "10");
        destinationAddresses.put("2", "20");
        request.setDestinationAddresses(destinationAddresses);

        return request;
    }

    private BulkSubjectMigrationRequest createSyncConceptMigrationRequest() {
        BulkSubjectMigrationRequest request = new BulkSubjectMigrationRequest();
        request.setSubjectIds(Arrays.asList(1L));

        Map<String, String> destinationSyncConcepts = new HashMap<>();
        destinationSyncConcepts.put("concept-1", "new-concept-1-value");
        destinationSyncConcepts.put("concept-2", "new-concept-2-value");
        request.setDestinationSyncConcepts(destinationSyncConcepts);

        return request;
    }

}
