package org.avni.server.importer.batch.zip;

import org.avni.server.importer.batch.model.BundleFile;
import org.avni.server.service.S3Service;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.JobBuilderException;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.transform.FlatFileFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.FileNotFoundException;
import java.io.IOException;

@Configuration
//@EnableBatchProcessing
@EnableScheduling
public class ZipJobBatchConfiguration {
    private final JobRepository jobRepository;
    private final S3Service s3Service;

    @Autowired
    public ZipJobBatchConfiguration(JobRepository jobRepository,
                                    @Qualifier("BatchS3Service") S3Service s3Service) {
        this.jobRepository = jobRepository;
        this.s3Service = s3Service;
    }

    @Bean
    @StepScope
    public ItemReader<BundleFile> zipItemReader(@Value("#{jobParameters['s3Key']}") String s3Key) throws IOException {
        return new ZipItemReader(s3Service.getObjectContent(s3Key));
    }

    @Bean
    public Job importZipJob(Step importZipStep, ZipJobCompletionNotificationListener zipJobCompletionNotificationListener) {
        return new JobBuilder("importZipJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(zipJobCompletionNotificationListener)
                .flow(importZipStep)
                .end()
                .build();
    }

    @Bean
    public Step importZipStep(ZipErrorFileWriterListener zipErrorFileWriterListener, ItemReader<BundleFile> zipItemReader, BundleZipFileImporter bundleZipFileImporter, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder("importZipStep", jobRepository)
                .<BundleFile, BundleFile>chunk(1, platformTransactionManager)
                .reader(zipItemReader)
                .writer(bundleZipFileImporter)
                .faultTolerant()
                .skip(Exception.class)
                .noSkip(FileNotFoundException.class)
                .noSkip(FlatFileParseException.class)
                .noSkip(FlatFileFormatException.class)
                .skipPolicy((error, count) -> true)
                .listener(zipErrorFileWriterListener)
                .transactionManager(platformTransactionManager)
                .build();
    }
}
