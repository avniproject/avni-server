package org.avni.server.importer.batch.userSubjectType;

import org.avni.server.framework.security.AuthService;
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
public class UserSubjectTypeCreateJobListener extends JobExecutionListenerSupport {
    private static final Logger logger = LoggerFactory.getLogger(UserSubjectTypeCreateJobListener.class);
    private final AuthService authService;

    @Value("#{jobParameters['subjectTypeId']}")
    private Long subjectTypeId;

    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;

    @Value("#{jobParameters['userId']}")
    private Long userId;

    @Autowired
    public UserSubjectTypeCreateJobListener(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        logger.info("User subject type create Job with uuid {} finished with staus {}", jobExecution.getJobParameters().getString("uuid"), jobExecution.getStatus());
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting user subject type create job with uuid {}", jobExecution.getJobParameters().getString("uuid"));
        authService.authenticateByUserId(userId, organisationUUID);
    }
}
