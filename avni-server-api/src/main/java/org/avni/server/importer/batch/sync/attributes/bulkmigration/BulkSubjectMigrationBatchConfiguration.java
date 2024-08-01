package org.avni.server.importer.batch.sync.attributes.bulkmigration;

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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableBatchProcessing
public class BulkSubjectMigrationBatchConfiguration {
    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final JobRepository jobRepository;

    @Autowired
    public BulkSubjectMigrationBatchConfiguration(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory, JobRepository jobRepository) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.jobRepository = jobRepository;
    }

    @Bean
    public JobLauncher bulkSubjectMigrationJobLauncher() {
        return new SimpleJobLauncher() {{
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
        return jobBuilderFactory
                .get("bulkSubjectMigrationJob")
                .incrementer(new RunIdIncrementer())
                .listener(bulkSubjectMigrationJobListener)
                .start(bulkSubjectMigrationStep)
                .build();
    }

    @Bean
    public Step bulkSubjectMigrationStep(BulkSubjectMigrationTasklet bulkSubjectMigrationTasklet) {
        return stepBuilderFactory.get("bulkSubjectMigrationStep")
                .tasklet(bulkSubjectMigrationTasklet)
                .build();
    }
}
