package org.avni.server.importer.batch.orgdata;

import org.avni.server.dao.AvniJobRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Runs the deletion of an organisation's data in the background (avni-webapp#1639), so the request that asks for it
 * no longer outlives the gateway timeout. The caller must have passed ImplementationService.assertCanDeleteImplementationData.
 */
@Service
public class DeleteOrgDataJobService {
    private final Job deleteOrgDataJob;
    private final JobLauncher bgJobLauncher;
    private final AvniJobRepository avniJobRepository;

    public DeleteOrgDataJobService(@Qualifier("deleteOrgDataJob") Job deleteOrgDataJob,
                                   @Qualifier("bgJobLauncher") JobLauncher bgJobLauncher,
                                   AvniJobRepository avniJobRepository) {
        this.deleteOrgDataJob = deleteOrgDataJob;
        this.bgJobLauncher = bgJobLauncher;
        this.avniJobRepository = avniJobRepository;
    }

    public String start(boolean deleteMetadata, boolean deleteAdminConfig) {
        String uuid = UUID.randomUUID().toString();
        User user = UserContextHolder.getUser();
        Organisation organisation = UserContextHolder.getOrganisation();
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("organisationUUID", organisation.getUuid())
                .addString("uuid", uuid)
                .addLong("userId", user.getId(), false)
                .addString("deleteMetadata", String.valueOf(deleteMetadata), false)
                .addString("deleteAdminConfig", String.valueOf(deleteAdminConfig), false)
                .toJobParameters();
        try {
            bgJobLauncher.run(deleteOrgDataJob, jobParameters);
        } catch (JobExecutionException e) {
            throw new RuntimeException(e);
        }
        return uuid;
    }

    public BatchJobStatus getLatestJobStatus(Organisation organisation) {
        return avniJobRepository.getJobStatus(DeleteOrgDataJobConfiguration.JOB_NAME, "organisationUUID", organisation.getUuid());
    }
}
