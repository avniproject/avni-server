package org.avni.server.service.metabase;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static java.lang.String.format;

@Component
public class CannedAnalyticsBatchJobService {
    private final JobLauncher bgJobLauncher;
    private final Job cannedAnalyticsSetupJob;
    private final Job cannedAnalyticsCreateQuestionOnlyJob;
    private final Job cannedAnalyticsTearDownJob;
    private final static Logger logger = LoggerFactory.getLogger(CannedAnalyticsBatchJobService.class);

    @Autowired
    public CannedAnalyticsBatchJobService(JobLauncher bgJobLauncher, @Qualifier("cannedAnalyticsSetupJob") Job cannedAnalyticsSetupJob, @Qualifier("cannedAnalyticsCreateQuestionOnlyJob") Job cannedAnalyticsCreateQuestionOnlyJob, @Qualifier("cannedAnalyticsTearDownJob") Job cannedAnalyticsTearDownJob) {
        this.bgJobLauncher = bgJobLauncher;
        this.cannedAnalyticsSetupJob = cannedAnalyticsSetupJob;
        this.cannedAnalyticsCreateQuestionOnlyJob = cannedAnalyticsCreateQuestionOnlyJob;
        this.cannedAnalyticsTearDownJob = cannedAnalyticsTearDownJob;
    }

    public void createSetupJob(Organisation organisation, User user) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        JobParameters jobParameters = getJobParameters(organisation, user);
        bgJobLauncher.run(cannedAnalyticsSetupJob, jobParameters);
    }

    public void createCreateQuestionOnlyJob(Organisation organisation, User user) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        JobParameters jobParameters = getJobParameters(organisation, user);
        bgJobLauncher.run(cannedAnalyticsCreateQuestionOnlyJob, jobParameters);
    }

    public void createTearDownJob(Organisation organisation, User user) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        JobParameters jobParameters = getJobParameters(organisation, user);
        bgJobLauncher.run(cannedAnalyticsTearDownJob, jobParameters);
    }

    private static JobParameters getJobParameters(Organisation organisation, User user) {
        String uuid = UUID.randomUUID().toString();
        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder()
                .addString("organisationUUID", organisation.getUuid())
                .addString("uuid", uuid)
                .addLong("userId", user.getId(), false);
        JobParameters jobParameters = jobParametersBuilder.toJobParameters();
        logger.info(format("Canned analytics job initiated! {uuid='%s'}", uuid));
        return jobParameters;
    }
}
