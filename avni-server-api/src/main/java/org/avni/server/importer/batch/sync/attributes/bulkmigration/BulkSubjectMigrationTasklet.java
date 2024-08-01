package org.avni.server.importer.batch.sync.attributes.bulkmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.service.S3Service;
import org.avni.server.service.SubjectMigrationService;
import org.avni.server.util.ObjectMapperSingleton;
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

import java.io.File;
import java.util.Map;

@Component
@JobScope
public class BulkSubjectMigrationTasklet implements Tasklet {
    private static final Logger logger = LoggerFactory.getLogger(BulkSubjectMigrationTasklet.class);
    private final SubjectMigrationService subjectMigrationService;
    private final S3Service s3Service;
    @Value("#{jobParameters['uuid']}")
    String uuid;

    @Value("#{jobParameters['mode']}")
    String mode;

    @Value("#{jobParameters['bulkSubjectMigrationParameters']}")
    SubjectMigrationRequest bulkSubjectMigrationParameters;

    @Autowired
    public BulkSubjectMigrationTasklet(SubjectMigrationService subjectMigrationService, S3Service s3Service) {
        this.subjectMigrationService = subjectMigrationService;
        this.s3Service = s3Service;
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        Map<String, String> failedMigrations = subjectMigrationService.bulkMigrate(SubjectMigrationService.BulkSubjectMigrationModes.valueOf(mode), bulkSubjectMigrationParameters);
        ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
        String fileName = uuid + ".json";
        File failedMigrationsFile = new File("/tmp/" + fileName);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(failedMigrationsFile, failedMigrations);
//        TODO upload file to S3
//        s3Service.uploadFile(failedMigrationsFile, fileName, "bulkuploads/subjectmigrations");
        return RepeatStatus.FINISHED;
    }
}
