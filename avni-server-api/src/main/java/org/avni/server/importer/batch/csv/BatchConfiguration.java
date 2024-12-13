package org.avni.server.importer.batch.csv;

import org.apache.commons.io.IOUtils;
import org.avni.server.importer.batch.csv.writer.CsvFileItemWriter;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.S3Service;
import org.avni.server.util.CollectionUtil;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.FlatFileFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.avni.server.importer.batch.csv.writer.UserAndCatchmentWriter.METADATA_ROW_START_STRING;
import static org.avni.server.service.ImportLocationsConstants.EXAMPLE;

@Configuration
//@EnableBatchProcessing
@EnableScheduling
public class BatchConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final S3Service s3Service;

    @Autowired
    public BatchConfiguration(JobRepository jobRepository,
                              PlatformTransactionManager platformTransactionManager,
                              @Qualifier("BatchS3Service")  S3Service s3Service) {
        this.jobRepository = jobRepository;
        this.platformTransactionManager = platformTransactionManager;
        this.s3Service = s3Service;
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Row> csvFileItemReader(@Value("#{jobParameters['s3Key']}") String s3Key) throws IOException {
        byte[] bytes = IOUtils.toByteArray(s3Service.getObjectContent(s3Key));
        String[] headers = this.getHeaders(new StringReader(new String(bytes)));
        int numberOfLinesToSkip = this.getNumberOfLinesToSkip(new StringReader(new String(bytes)));
        DefaultLineMapper<Row> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(new DelimitedLineTokenizer());
        lineMapper.setFieldSetMapper(fieldSet -> new Row(headers, fieldSet.getValues()));

        return new FlatFileItemReaderBuilder<Row>()
                .name("csvFileItemReader")
                .resource(new ByteArrayResource(bytes))
                .linesToSkip(numberOfLinesToSkip)
                .lineMapper(lineMapper)
                .build();
    }

    @Bean
    public Job importJob(ErrorFileCreatorListener listener, Step importStep) {
        return new JobBuilder("importJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(importStep)
                .end()
                .build();
    }

    @Bean
    public Step importStep(FlatFileItemReader<Row> csvFileItemReader,
                           CsvFileItemWriter csvFileItemWriter,
                           ErrorFileWriterListener errorFileWriterListener) {
        return new StepBuilder("importStep", jobRepository)
                .<Row, Row>chunk(1, platformTransactionManager)
                .reader(csvFileItemReader)
                .writer(csvFileItemWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skip(RuntimeException.class)
                .noSkip(FileNotFoundException.class)
                .noSkip(FlatFileParseException.class)
                .noSkip(FlatFileFormatException.class)
                .skipPolicy((error, count) -> true)
                .listener(errorFileWriterListener)
                .build();
    }

    @Bean
    public JobLauncher bgJobLauncher() {
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

    private String[] getHeaders(Reader reader) throws IOException {
        BufferedReader csvReader = new BufferedReader(reader);
        String headerLine = csvReader.readLine();
        csvReader.close();

        final List<String> headers = new ArrayList<>();
        new DelimitedLineTokenizer() {{
            headers.addAll(doTokenize(headerLine));
        }};

        return headers.toArray(new String[]{});
    }

    private int getNumberOfLinesToSkip(Reader reader) throws IOException {
        int linesToSkip = 1;
        try(BufferedReader csvReader = new BufferedReader(reader)) {
            csvReader.readLine(); //Retain always to move from Header line to descriptor line
            String possibleDescriptorLine = csvReader.readLine();

            final List<String> descriptors = new ArrayList<>();
            new DelimitedLineTokenizer() {{
                descriptors.addAll(doTokenize(possibleDescriptorLine));
            }};

            if (CollectionUtil.anyStartsWith(descriptors, EXAMPLE)
                    || CollectionUtil.anyStartsWith(descriptors, METADATA_ROW_START_STRING)) {
                linesToSkip++;
            }
        }
        return linesToSkip;
    }
}
