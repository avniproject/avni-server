package org.avni.server.importer.batch.sync.attributes.bulkmigration;

import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.service.SubjectMigrationService;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@JobScope
public class BulkSubjectMigrationTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(BulkSubjectMigrationTasklet.class);
    private final SubjectMigrationService subjectMigrationService;

    @Value("#{jobParameters['uuid']}")
    String uuid;

    @Value("#{jobParameters['mode']}")
    String mode;

    @Value("#{jobParameters['bulkSubjectMigrationParameters']}")
    BulkSubjectMigrationRequest bulkSubjectMigrationParameters;

    @Autowired
    public BulkSubjectMigrationTasklet(SubjectMigrationService subjectMigrationService, BulkUploadS3Service s3Service) {
        this.subjectMigrationService = subjectMigrationService;
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        Map<String, String> failedMigrations = subjectMigrationService.bulkMigrate(SubjectMigrationService.BulkSubjectMigrationModes.valueOf(mode), bulkSubjectMigrationParameters);
        chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext().put("failedMigrations", failedMigrations);
        return RepeatStatus.FINISHED;
    }
}
