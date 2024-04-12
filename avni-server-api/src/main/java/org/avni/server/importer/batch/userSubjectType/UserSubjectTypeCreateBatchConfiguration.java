package org.avni.server.importer.batch.userSubjectType;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
@EnableBatchProcessing
public class UserSubjectTypeCreateBatchConfiguration {
    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final JobRepository jobRepository;

    @Autowired
    public UserSubjectTypeCreateBatchConfiguration(JobBuilderFactory jobBuilderFactory,
                                                   StepBuilderFactory stepBuilderFactory,
                                                   JobRepository jobRepository) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.jobRepository = jobRepository;
    }

    @Bean
    public JobLauncher userSubjectTypeCreateJobLauncher() {
        return new SimpleJobLauncher() {{
            setJobRepository(jobRepository);
            setTaskExecutor(new ThreadPoolTaskExecutor() {{
                setCorePoolSize(1);
                setMaxPoolSize(1);
                setQueueCapacity(1);
                initialize();
            }});
        }};
    }

    @Bean
    public Job userSubjectTypeCreateJob(UserSubjectTypeCreateJobListener listener, Step userSubjectTypeCreateStep) {
        return jobBuilderFactory
                .get("userSubjectTypeCreateJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(userSubjectTypeCreateStep)
                .build();
    }

    @Bean
    public Step userSubjectTypeCreateStep(UserSubjectTypeCreateTasklet tasklet) {
        return stepBuilderFactory.get("userSubjectTypeCreateStep")
                .tasklet(tasklet)
                .build();
    }
}

