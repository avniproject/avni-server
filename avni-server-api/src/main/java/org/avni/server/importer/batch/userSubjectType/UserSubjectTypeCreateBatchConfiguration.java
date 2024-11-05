package org.avni.server.importer.batch.userSubjectType;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableScheduling
//@EnableBatchProcessing
public class UserSubjectTypeCreateBatchConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    @Autowired
    public UserSubjectTypeCreateBatchConfiguration(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
    }

    @Bean
    public JobLauncher userSubjectTypeCreateJobLauncher() {
        return new TaskExecutorJobLauncher() {{
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
        return new JobBuilder("userSubjectTypeCreateJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(userSubjectTypeCreateStep)
                .build();
    }

    @Bean
    public Step userSubjectTypeCreateStep(UserSubjectTypeCreateTasklet tasklet) {
        return new StepBuilder("userSubjectTypeCreateStep", jobRepository)
                .tasklet(tasklet, platformTransactionManager)
                .build();
    }
}

