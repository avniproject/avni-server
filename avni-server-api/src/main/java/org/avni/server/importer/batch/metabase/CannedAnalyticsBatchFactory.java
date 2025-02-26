package org.avni.server.importer.batch.metabase;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.PlatformTransactionManager;

public class CannedAnalyticsBatchFactory {
    public static Job createCannedAnalyticsJob(String jobName, JobRepository jobRepository, CannedAnalyticsNotificationListener cannedAnalyticsNotificationListener, Step step) {
        return new JobBuilder(jobName, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(cannedAnalyticsNotificationListener)
                .start(step)
                .build();
    }

    public static Step createCannedAnalyticsStep(String stepName, JobRepository jobRepository, Tasklet cannedAnalyticsRunner, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder(stepName, jobRepository)
                .tasklet(cannedAnalyticsRunner, platformTransactionManager)
                .build();
    }
}
