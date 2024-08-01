package org.avni.server.importer.batch.sync.attributes.bulkmigration;

import org.avni.server.framework.security.AuthService;
import org.avni.server.web.request.SubjectMigrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class BulkSubjectMigrationJobListener extends JobExecutionListenerSupport {
    private static final Logger logger = LoggerFactory.getLogger(BulkSubjectMigrationJobListener.class);
    private final AuthService authService;

    @Value("#{jobParameters['uuid']}")
    private String uuid;

    @Value("#{jobParameters['mode']}")
    private String mode;

    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Value("#{jobParameters['userId']}")
    private Long userId;

    @Value("#{jobParameters['bulkSubjectMigrationParameters']}")
    private SubjectMigrationRequest bulkSubjectMigrationParameters;

    @Autowired
    public BulkSubjectMigrationJobListener(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting Bulk Subject Migration Job {} mode: {}. Migrating {} subjects", uuid, mode, bulkSubjectMigrationParameters.getSubjectIds().size());
        authService.authenticateByUserId(userId, organisationUUID);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        logger.info("Finished Bulk Subject Migration Job {} mode: {} exitStatus: {} createTime: {} startTime: {} endTime: {}",
                uuid, mode, jobExecution.getExitStatus(), jobExecution.getCreateTime(), jobExecution.getStartTime(), jobExecution.getEndTime());
    }
}
