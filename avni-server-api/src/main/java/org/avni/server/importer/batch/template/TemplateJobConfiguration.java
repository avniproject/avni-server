package org.avni.server.importer.batch.template;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
//@EnableBatchProcessing
public class TemplateJobConfiguration {
    private final JobRepository jobRepository;
    private final TemplateJobListener templateJobListener;
    private final TemplateJobTasklet templateJobTasklet;
    private final PlatformTransactionManager platformTransactionManager;
    private final Job importZipJob;

    @Autowired
    public TemplateJobConfiguration(JobRepository jobRepository,
                                    TemplateJobListener templateJobListener,
                                    TemplateJobTasklet templateJobTasklet,
                                    PlatformTransactionManager platformTransactionManager, Job importZipJob) {
        this.jobRepository = jobRepository;
        this.templateJobListener = templateJobListener;
        this.templateJobTasklet = templateJobTasklet;
        this.platformTransactionManager = platformTransactionManager;
        this.importZipJob = importZipJob;
    }

    @Bean
    public Step cleanUpStep() {
        return new StepBuilder("cleanUpStep", jobRepository)
                .tasklet(templateJobTasklet, platformTransactionManager)
                .build();
    }

    @Bean
    public Step importBundleStep() {
        return new StepBuilder("importBundleStep", jobRepository)
                .job(importZipJob)
                .listener(templateJobListener)
                .build();
    }


    @Bean
    public Job applyTemplateJob() {
        return new JobBuilder("applyTemplateJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(templateJobListener)
                .start(cleanUpStep())
                .next(importBundleStep())
                .build();
    }
}
