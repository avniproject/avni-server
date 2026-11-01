package org.avni.server.importer.batch.orgdata;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DeleteOrgDataJobConfiguration {
    public static final String JOB_NAME = "deleteOrgDataJob";

    private final JobRepository jobRepository;
    private final DeleteOrgDataJobListener deleteOrgDataJobListener;
    private final DeleteOrgDataTasklet deleteOrgDataTasklet;
    private final PlatformTransactionManager platformTransactionManager;

    public DeleteOrgDataJobConfiguration(JobRepository jobRepository,
                                         DeleteOrgDataJobListener deleteOrgDataJobListener,
                                         DeleteOrgDataTasklet deleteOrgDataTasklet,
                                         PlatformTransactionManager platformTransactionManager) {
        this.jobRepository = jobRepository;
        this.deleteOrgDataJobListener = deleteOrgDataJobListener;
        this.deleteOrgDataTasklet = deleteOrgDataTasklet;
        this.platformTransactionManager = platformTransactionManager;
    }

    @Bean
    public Step deleteOrgDataStep() {
        return new StepBuilder("deleteOrgDataStep", jobRepository)
                .tasklet(deleteOrgDataTasklet, platformTransactionManager)
                .build();
    }

    @Bean
    public Job deleteOrgDataJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(deleteOrgDataJobListener)
                .start(deleteOrgDataStep())
                .build();
    }
}
