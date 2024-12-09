package org.avni.server.importer.batch.sync.attributes.bulkmigration;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
//@EnableBatchProcessing
public class BulkSubjectMigrationBatchConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    @Autowired
    public BulkSubjectMigrationBatchConfiguration(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
    }

    @Bean
    public JobLauncher bulkSubjectMigrationJobLauncher() {
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
    public Job bulkSubjectMigrationJob(BulkSubjectMigrationJobListener bulkSubjectMigrationJobListener, Step bulkSubjectMigrationStep) {
        return new JobBuilder("bulkSubjectMigrationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(bulkSubjectMigrationJobListener)
                .start(bulkSubjectMigrationStep)
                .build();
    }

    @Bean
    public Step bulkSubjectMigrationStep(BulkSubjectMigrationTasklet bulkSubjectMigrationTasklet) {
        return new StepBuilder("bulkSubjectMigrationStep", jobRepository)
                .tasklet(bulkSubjectMigrationTasklet, platformTransactionManager)
                .build();
    }
}
