package org.avni.server.importer.batch.metabase;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
//@EnableBatchProcessing
@EnableScheduling
public class CannedAnalyticsBatchConfiguration {
    private final JobRepository jobRepository;

    @Autowired
    public CannedAnalyticsBatchConfiguration(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Bean
    public Job cannedAnalyticsJob(Step cannedAnalyticsStep, CannedAnalyticsNotificationListener cannedAnalyticsNotificationListener) {
        return new JobBuilder("cannedAnalyticsJob", jobRepository)
                .listener(cannedAnalyticsNotificationListener)
                .flow(cannedAnalyticsStep)
                .end()
                .build();
    }

    @Bean
    public Step cannedAnalyticsStep(CannedAnalyticsRunner cannedAnalyticsRunner, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder("cannedAnalyticsStep", jobRepository)
                .<Void, Void>chunk(1, platformTransactionManager)
                .reader(() -> null)
                .writer(cannedAnalyticsRunner)
                .faultTolerant()
                .skip(Exception.class)
                .skipPolicy((error, count) -> true)
                .transactionManager(platformTransactionManager)
                .build();
    }
}
