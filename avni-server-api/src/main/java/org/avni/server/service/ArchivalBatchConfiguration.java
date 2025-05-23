package org.avni.server.service;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ArchivalBatchConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    public ArchivalBatchConfiguration(JobRepository jobRepository,
                                      PlatformTransactionManager platformTransactionManager) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
    }
//quartzjob (maybe no need of either quartz and batch job)
    //trigger an api to do this
    @Bean
    public JobLauncher archivalJobLauncher() {
        return new TaskExecutorJobLauncher() {{
            setJobRepository(jobRepository);
            setTaskExecutor(new ThreadPoolTaskExecutor() {{
                setCorePoolSize(1);
                setMaxPoolSize(1);
                setQueueCapacity(100);
                initialize();
            }});
        }};
    }

    @Bean
    public Job archivalJob(ArchivalJobListener listener, Step archivalStep) {
        return new JobBuilder("archivalJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(archivalStep)
                .build();
    }

    @Bean
    public Step archivalStep(ArchivalTasklet tasklet) {
        return new StepBuilder("archivalStep", jobRepository)
                .tasklet(tasklet, platformTransactionManager)
                .build();
    }
}