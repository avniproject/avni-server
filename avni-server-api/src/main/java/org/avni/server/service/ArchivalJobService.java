package org.avni.server.service;

import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ArchivalJobService {
    private static final Logger logger = LoggerFactory.getLogger(ArchivalJobService.class);

    private final JobLauncher archivalJobLauncher;
    private final Job archivalJob;

    public ArchivalJobService(JobLauncher archivalJobLauncher, Job archivalJob) {
        this.archivalJobLauncher = archivalJobLauncher;
        this.archivalJob = archivalJob;
    }

    public void triggerArchivalJob() {
        try {
            User currentUser = UserContextHolder.getUser();
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("uuid", UUID.randomUUID().toString())
                    .addString("organisationUUID", UserContextHolder.getOrganisation().getUuid())
                    .addLong("userId", currentUser.getId())
                    .addLong("startTime", System.currentTimeMillis())
                    .toJobParameters();

            archivalJobLauncher.run(archivalJob, jobParameters);
        } catch (Exception e) {
            logger.error("Error triggering archival job", e);
            throw new RuntimeException("Failed to trigger archival job", e);
        }
    }
}