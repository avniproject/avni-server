package org.avni.server.importer.batch.metabase;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
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
    public Job cannedAnalyticsSetupJob(Step cannedAnalyticsSetupStep, CannedAnalyticsNotificationListener cannedAnalyticsNotificationListener) {
        return CannedAnalyticsBatchFactory.createCannedAnalyticsJob("cannedAnalyticsSetupJob", jobRepository, cannedAnalyticsNotificationListener, cannedAnalyticsSetupStep);
    }

    @Bean
    public Step cannedAnalyticsSetupStep(CannedAnalyticsSetupTasklet cannedAnalyticsRunner, PlatformTransactionManager platformTransactionManager) {
        return CannedAnalyticsBatchFactory.createCannedAnalyticsStep("cannedAnalyticsSetupStep", jobRepository, cannedAnalyticsRunner, platformTransactionManager);
    }

    @Bean
    public Job cannedAnalyticsTearDownJob(Step cannedAnalyticsSetupStep, CannedAnalyticsNotificationListener cannedAnalyticsNotificationListener) {
        return CannedAnalyticsBatchFactory.createCannedAnalyticsJob("cannedAnalyticsTearDownJob", jobRepository, cannedAnalyticsNotificationListener, cannedAnalyticsSetupStep);
    }

    @Bean
    public Step cannedAnalyticsTearDownStep(CannedAnalyticsSetupTasklet cannedAnalyticsRunner, PlatformTransactionManager platformTransactionManager) {
        return CannedAnalyticsBatchFactory.createCannedAnalyticsStep("cannedAnalyticsTearDownStep", jobRepository, cannedAnalyticsRunner, platformTransactionManager);
    }

    @Bean
    public Job cannedAnalyticsCreateQuestionOnlyJob(Step cannedAnalyticsSetupStep, CannedAnalyticsNotificationListener cannedAnalyticsNotificationListener) {
        return CannedAnalyticsBatchFactory.createCannedAnalyticsJob("cannedAnalyticsCreateQuestionOnlyJob", jobRepository, cannedAnalyticsNotificationListener, cannedAnalyticsSetupStep);
    }

    @Bean
    public Step cannedAnalyticsCreateQuestionOnlyStep(CannedAnalyticsSetupTasklet cannedAnalyticsRunner, PlatformTransactionManager platformTransactionManager) {
        return CannedAnalyticsBatchFactory.createCannedAnalyticsStep("cannedAnalyticsCreateQuestionOnlyStep", jobRepository, cannedAnalyticsRunner, platformTransactionManager);
    }
}
