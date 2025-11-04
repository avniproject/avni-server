package org.avni.server.service;

import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.TemplateOrganisationRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.TemplateOrganisation;
import org.avni.server.web.request.TemplateOrganisationContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TemplateOrganisationService {
    private final TemplateOrganisationRepository templateOrganisationRepository;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public TemplateOrganisationService(TemplateOrganisationRepository templateOrganisationRepository,
                                     OrganisationRepository organisationRepository) {
        this.templateOrganisationRepository = templateOrganisationRepository;
        this.organisationRepository = organisationRepository;
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
}
