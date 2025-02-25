package org.avni.server.importer.batch.metabase;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class CannedAnalyticsNotificationListener implements JobExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(CannedAnalyticsNotificationListener.class);

    @Override
    public void afterJob(JobExecution jobExecution) {
        logger.info("completed canned analytics job uuid {} {}", jobExecution.getJobParameters().getString("uuid"), jobExecution.getStatus());
        ExitStatus exitStatus = jobExecution.getExitStatus();
        if (exitStatus.equals(ExitStatus.FAILED)) {
            jobExecution.getStepExecutions().forEach(stepExecution -> {
                ExitStatus stepExitStatus = stepExecution.getExitStatus();
                if (stepExitStatus.getExitCode().equals("FAILED")) {
                    logger.warn("CannedAnalyticsJob uuid {} {}", jobExecution.getJobParameters().getString("uuid"), stepExitStatus.getExitDescription());
                }
            });
        }
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        logger.info("starting canned analytics job uuid {}", jobExecution.getJobParameters().getString("uuid"));
    }
}
