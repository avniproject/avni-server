package org.avni.server.importer.batch;

import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.avni.server.dao.AvniJobRepository;
import org.avni.server.dao.JobStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ObjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

import static java.lang.String.format;
import static org.springframework.batch.core.BatchStatus.*;

@Service
public class JobService {
    private final static Logger logger = LoggerFactory.getLogger(JobService.class);
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final Job importJob;
    private final Job importZipJob;
    private final JobLauncher bgJobLauncher;
    private final AvniJobRepository avniJobRepository;

    @Autowired
    public JobService(JobExplorer jobExplorer, JobRepository jobRepository, Job importJob, Job importZipJob, JobLauncher bgJobLauncher, AvniJobRepository avniJobRepository) {
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.importJob = importJob;
        this.importZipJob = importZipJob;
        this.bgJobLauncher = bgJobLauncher;
        this.avniJobRepository = avniJobRepository;
    }

    public void retryJobsFailedInLast2Hours() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        for (JobExecution jobExecution : jobExplorer.findRunningJobExecutions(importJob.getName())) {
            BatchStatus status = jobExecution.getStatus();
            LocalDateTime lastUpdated = jobExecution.getLastUpdated();
            LocalDateTime nowMinus2Hours = LocalDateTime.now().minusHours(2);
            if (nowMinus2Hours.isBefore(lastUpdated) && Arrays.asList(STARTING, STARTED, UNKNOWN).contains(status)) {
                jobExecution.upgradeStatus(FAILED);
                jobExecution.setEndTime(LocalDateTime.now());
                jobRepository.update(jobExecution);
                for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                    if (Arrays.asList(STARTING, STARTED, UNKNOWN).contains(stepExecution.getStatus())) {
                        stepExecution.upgradeStatus(FAILED);
                        stepExecution.setEndTime(LocalDateTime.now());
                        jobRepository.update(stepExecution);
                    }
                }
                bgJobLauncher.run(importJob, jobExecution.getJobParameters());
            }
        }
    }

    public JobExecution create(String uuid, String type, String fileName, ObjectInfo s3FileInfo, Long userId, String organisationUUID, boolean autoApprove, String locationUploadMode, String locationHierarchy) throws JobParametersInvalidException, JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException {
        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder()
                .addString("organisationUUID", organisationUUID)
                .addString("uuid", uuid)
                .addString("fileName", fileName, false)
                .addString("s3Key", s3FileInfo.getKey(), false)
                .addLong("noOfLines", s3FileInfo.getNoOfLines(), false)
                .addLong("userId", userId, false)
                .addString("type", type, false)
                .addString("autoApprove", String.valueOf(autoApprove))
                .addString("locationUploadMode", locationUploadMode);
        if (Objects.nonNull(locationHierarchy)) {
            jobParametersBuilder.addString("locationHierarchy", locationHierarchy);
        }
        JobParameters jobParameters = jobParametersBuilder.toJobParameters();
        logger.info(format("Bulk upload initiated! Job{type='%s',uuid='%s',fileName='%s'}", type, uuid, fileName));

        return type.equals("metadataZip") ? bgJobLauncher.run(importZipJob, jobParameters) : bgJobLauncher.run(importJob, jobParameters);
    }

    @Transactional
    public Page<JobStatus> getAll(@NotNull Pageable pageable) {
        String jobFilterCondition = " and subjectTypeUUID = '' ";
        return avniJobRepository.getJobStatuses(UserContextHolder.getUser(), jobFilterCondition, pageable);
    }
}
