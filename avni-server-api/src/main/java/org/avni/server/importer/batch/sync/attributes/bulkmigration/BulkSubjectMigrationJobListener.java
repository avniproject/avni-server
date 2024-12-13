package org.avni.server.importer.batch.sync.attributes.bulkmigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.lang.String.format;

@Component
@JobScope
public class BulkSubjectMigrationJobListener extends JobExecutionListenerSupport {
    private static final Logger logger = LoggerFactory.getLogger(BulkSubjectMigrationJobListener.class);
    private final AuthService authService;
    private final BulkUploadS3Service s3Service;

    @Value("#{jobParameters['uuid']}")
    private String uuid;

    @Value("#{jobParameters['mode']}")
    private String mode;

    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Value("#{jobParameters['userId']}")
    private Long userId;

    @Value("#{jobParameters['fileName']}")
    private String fileName;

    @Value("#{jobParameters['bulkSubjectMigrationParameters']}")
    private BulkSubjectMigrationRequest bulkSubjectMigrationParameters;

    @Autowired
    public BulkSubjectMigrationJobListener(AuthService authService, BulkUploadS3Service s3Service) {
        this.authService = authService;
        this.s3Service = s3Service;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting Bulk Subject Migration Job {} mode: {}. Migrating {} subjects", uuid, mode, bulkSubjectMigrationParameters.getSubjectIds().size());
        authService.authenticateByUserId(userId, organisationUUID);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Map<String, String> failedMigrations = (Map<String, String>) jobExecution.getExecutionContext().get("failedMigrations");
        logger.info("Finished Bulk Subject Migration Job {} mode: {} failedCount: {} exitStatus: {} waitTime: {}ms processingTime: {}ms fileName: {}",
                uuid, mode, failedMigrations.size(), jobExecution.getExitStatus(),
                DateTimeUtil.getMilliSecondsDuration(jobExecution.getCreateTime(), jobExecution.getEndTime()),
                DateTimeUtil.getMilliSecondsDuration(jobExecution.getStartTime(), jobExecution.getEndTime()), fileName);
        try {
            writeFailuresToFileAndUploadToS3(failedMigrations);
        } catch (Exception e) {
            logger.error("Failed to write bulk subject migration failures to file and upload", e);
        }

    }

    private void writeFailuresToFileAndUploadToS3(Map<String, String> failedMigrations) throws IOException {
        ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
        File failedMigrationsFile = new File(format("%s/%s", System.getProperty("java.io.tmpdir"), fileName));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(failedMigrationsFile, failedMigrations);
        s3Service.uploadFile(failedMigrationsFile, fileName, "bulksubjectmigrations");
    }
}
