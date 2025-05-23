package org.avni.server.service;

import org.avni.server.framework.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class ArchivalJobListener extends JobExecutionListenerSupport {
    private static final Logger logger = LoggerFactory.getLogger(ArchivalJobListener.class);
    private final AuthService authService;

    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Value("#{jobParameters['userId']}")
    private Long userId;

    public ArchivalJobListener(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting archival job with uuid {}", jobExecution.getJobParameters().getString("uuid"));
        authService.authenticateByUserId(userId, organisationUUID);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            logger.info("Archival job completed successfully");
        } else {
            logger.error("Archival job failed with status: {}", jobExecution.getStatus());
        }
    }
}