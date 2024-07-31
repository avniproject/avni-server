package org.avni.server.importer.batch.sync.attributes.bulkmigration;

import org.avni.server.service.SubjectMigrationService;
import org.avni.server.web.request.SubjectMigrationRequest;
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

import java.util.Set;
import java.util.stream.Collectors;

@Component
@JobScope
public class BulkSubjectMigrationTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(BulkSubjectMigrationTasklet.class);
    private final SubjectMigrationService subjectMigrationService;
    @Value("#{jobParameters['mode']}")
    String mode;

    @Value("#{jobParameters['bulkSubjectMigrationParameters']}")
    SubjectMigrationRequest bulkSubjectMigrationParameters;

    @Autowired
    public BulkSubjectMigrationTasklet(SubjectMigrationService subjectMigrationService) {
        this.subjectMigrationService = subjectMigrationService;
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        logger.info(bulkSubjectMigrationParameters.getSubjectUuids().toString());
        Set<String> migrationCompletedSubjectUuids = subjectMigrationService.bulkMigrate(SubjectMigrationService.BulkSubjectMigrationModes.valueOf(mode), bulkSubjectMigrationParameters);
        Set<String> migrationFailedSubjectUuids = bulkSubjectMigrationParameters.getSubjectUuids().stream().filter(s -> !migrationCompletedSubjectUuids.contains(s)).collect(Collectors.toSet());
        logger.info("Failed to migrate subject uuids: {}", migrationFailedSubjectUuids);
        return RepeatStatus.FINISHED;
    }
}
