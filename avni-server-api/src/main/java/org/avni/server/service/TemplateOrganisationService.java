package org.avni.server.service;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.TemplateOrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.TemplateOrganisation;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.JobService;
import org.avni.server.web.request.TemplateOrganisationContract;
import org.joda.time.DateTime;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class TemplateOrganisationService {
    private final TemplateOrganisationRepository templateOrganisationRepository;
    private final OrganisationRepository organisationRepository;
    private final BundleService bundleService;
    private final BulkUploadS3Service bulkUploadS3Service;
    private final JobService jobService;
    private final ImplementationService implementationService;
    private final JobLauncher bgJobLauncher;

    @Autowired
    public TemplateOrganisationService(TemplateOrganisationRepository templateOrganisationRepository,
                                       OrganisationRepository organisationRepository, BundleService bundleService, BulkUploadS3Service bulkUploadS3Service, JobService jobService, ImplementationService implementationService, JobLauncher bgJobLauncher) {
        this.templateOrganisationRepository = templateOrganisationRepository;
        this.organisationRepository = organisationRepository;
        this.bundleService = bundleService;
        this.bulkUploadS3Service = bulkUploadS3Service;
        this.jobService = jobService;
        this.implementationService = implementationService;
        this.bgJobLauncher = bgJobLauncher;
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
//        implementationService.deleteImplementationData(true, true);
        MultipartFile file = new MockMultipartFile("clone.zip", "clone.zip", "application/zip", bundle.toByteArray());
        String uuid = UUID.randomUUID().toString();
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        ObjectInfo storedFileInfo = bulkUploadS3Service.uploadZip(file, uuid);
//        jobService.create(uuid, "metadataZip", file.getOriginalFilename(), storedFileInfo, user.getId(), organisation.getUuid(), false, null, null, null);
        return uuid;
    }
}
