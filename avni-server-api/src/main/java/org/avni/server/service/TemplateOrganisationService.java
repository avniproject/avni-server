package org.avni.server.service;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.TemplateOrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.TemplateOrganisation;
import org.avni.server.domain.User;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.batch.BatchJobService;
import org.avni.server.web.request.TemplateOrganisationContract;
import org.joda.time.DateTime;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TemplateOrganisationService {
    private final TemplateOrganisationRepository templateOrganisationRepository;
    private final OrganisationRepository organisationRepository;
    private final BundleService bundleService;
    private final BulkUploadS3Service bulkUploadS3Service;
    private final Job applyTemplateJob;
    private final JobLauncher bgJobLauncher;
    private static final Logger logger = LoggerFactory.getLogger(TemplateOrganisationService.class);
    private final BatchJobService batchJobService;

    @Autowired
    public TemplateOrganisationService(TemplateOrganisationRepository templateOrganisationRepository,
                                       OrganisationRepository organisationRepository, BundleService bundleService, BulkUploadS3Service bulkUploadS3Service, Job applyTemplateJob, JobLauncher bgJobLauncher, BatchJobService batchJobService) {
        this.templateOrganisationRepository = templateOrganisationRepository;
        this.organisationRepository = organisationRepository;
        this.bundleService = bundleService;
        this.bulkUploadS3Service = bulkUploadS3Service;
        this.applyTemplateJob = applyTemplateJob;
        this.bgJobLauncher = bgJobLauncher;
        this.batchJobService = batchJobService;
    }

    @Transactional
    public TemplateOrganisation save(TemplateOrganisationContract request) {
        request.validate();
        TemplateOrganisation templateOrganisation = new TemplateOrganisation();
        TemplateOrganisation updatedTemplateOrganisation = setFields(templateOrganisation, request);
        return templateOrganisationRepository.save(updatedTemplateOrganisation);
    }

    @Transactional
    public TemplateOrganisation update(Long id, TemplateOrganisationContract request) {
        request.validate();
        Optional<TemplateOrganisation> templateOrganisation = templateOrganisationRepository.findById(id);
        if (templateOrganisation.isEmpty()) {
            throw new IllegalArgumentException(String.format("TemplateOrganisation not found with id %d", id));
        }
        return setFields(templateOrganisation.orElse(null), request);
    }

    private TemplateOrganisation setFields(TemplateOrganisation templateOrganisation, TemplateOrganisationContract request) {
        Organisation organisation = organisationRepository.findOne(request.getOrganisationId());
        if (organisation == null) {
            throw new IllegalArgumentException(String.format("Organisation not found with id %d", request.getOrganisationId()));
        }
        
        templateOrganisation.setName(request.getName());
        templateOrganisation.setDescription(request.getDescription());
        templateOrganisation.setSummary(request.getSummary());
        templateOrganisation.setActive(request.isActive());
        templateOrganisation.setOrganisation(organisation);
        // ignoring isVoided intentionally for this entity. Control templates using the 'active' flag
        if (templateOrganisation.getUuid() == null) {
            templateOrganisation.setUuid(request.getUuid() == null ? UUID.randomUUID().toString() : request.getUuid());
        }
        
        if (templateOrganisation.getCreatedDateTime() == null) {
            templateOrganisation.setCreatedDateTime(DateTime.now());
        }
        
        templateOrganisation.setLastModifiedDateTime(DateTime.now());
        return templateOrganisation;
    }


    public String applyTemplate(TemplateOrganisation templateOrganisation) throws IOException, JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        ByteArrayOutputStream bundle = bundleService.generateBundleForOrg(templateOrganisation.getOrganisation().getId());
        String fileName = templateOrganisation.getName() + ".zip";
        MultipartFile file = new MockMultipartFile(fileName, fileName, "application/zip", bundle.toByteArray());
        String uuid = UUID.randomUUID().toString();
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        ObjectInfo storedFileInfo = bulkUploadS3Service.uploadZip(file, uuid);
        bgJobLauncher.run(applyTemplateJob, getJobParameters(organisation, user, storedFileInfo, uuid));
        logger.debug("Triggered job to apply template {} to organisation: {}", templateOrganisation.getName(), organisation.getName());
        return uuid;
    }

    private static JobParameters getJobParameters(Organisation organisation, User user, ObjectInfo storedFileInfo, String uuid) {
        JobParametersBuilder jobParametersBuilder = new JobParametersBuilder()
                .addString("organisationUUID", organisation.getUuid())
                .addString("uuid", uuid)
                .addString("s3Key", storedFileInfo.getKey(), false)
                .addLong("userId", user.getId(), false);
        return jobParametersBuilder.toJobParameters();
    }

    public Map<String, BatchJobStatus> getApplyTemplateJobStatus(Organisation organisation) {
        return batchJobService.getApplyTemplateJobStatus(organisation);
    }
}
