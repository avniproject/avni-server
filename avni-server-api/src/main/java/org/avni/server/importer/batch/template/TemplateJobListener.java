package org.avni.server.importer.batch.template;

import org.avni.server.framework.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class TemplateJobListener extends JobExecutionListenerSupport {
    private static final Logger logger = LoggerFactory.getLogger(TemplateJobListener.class);

    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;
    private final AuthService authService;

    @Autowired
    public TemplateJobListener(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting template application job");
        authService.authenticateByUserId(userId, organisationUUID);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            logger.info("Template application job completed successfully");
        } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
            logger.error("Template application job failed with following exceptions:");
            jobExecution.getAllFailureExceptions().forEach(throwable -> 
                logger.error(throwable.getMessage(), throwable)
            );
        }
    }
}
