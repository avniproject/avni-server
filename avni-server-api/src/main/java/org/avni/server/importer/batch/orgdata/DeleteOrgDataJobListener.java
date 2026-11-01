package org.avni.server.importer.batch.orgdata;

import org.avni.server.framework.security.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@JobScope
public class DeleteOrgDataJobListener implements JobExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(DeleteOrgDataJobListener.class);

    @Value("#{jobParameters['userId']}")
    private Long userId;
    @Value("#{jobParameters['organisationUUID']}")
    private String organisationUUID;
    private final AuthService authService;

    public DeleteOrgDataJobListener(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("Starting organisation data deletion job for organisation {}", organisationUUID);
        authService.authenticateByUserId(userId, organisationUUID);
    }

    /**
     * The status endpoint returns the exit message. Spring Batch puts a stack trace there on failure; replace it with
     * the reason, which is what the web app shows (e.g. "Production or UAT organisation's data cannot be deleted").
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.FAILED) {
            logger.info("Organisation data deletion job ended with status {}", jobExecution.getStatus());
            return;
        }
        List<Throwable> failures = jobExecution.getAllFailureExceptions();
        failures.forEach(throwable -> logger.error("Organisation data deletion job failed", throwable));
        String reason = failures.isEmpty() ? "Deleting organisation data failed" : messageOf(failures.get(0));
        jobExecution.setExitStatus(new ExitStatus(ExitStatus.FAILED.getExitCode(), reason));
    }

    private static String messageOf(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
