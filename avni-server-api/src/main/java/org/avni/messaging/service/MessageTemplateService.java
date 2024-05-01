package org.avni.messaging.service;

import org.avni.messaging.contract.glific.GlificMessageTemplate;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.repository.GlificMessageTemplateRepository;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class MessageTemplateService {

    private GlificMessageTemplateRepository messageTemplateRepository;
    private OrganisationConfigService organisationConfigService;

    @Autowired
    public MessageTemplateService(GlificMessageTemplateRepository messageTemplateRepository, OrganisationConfigService organisationConfigService) {
        this.messageTemplateRepository = messageTemplateRepository;
        this.organisationConfigService = organisationConfigService;
    }

    public List<GlificMessageTemplate> findAll() {
        try {
            return organisationConfigService.isMessagingEnabled() ?
                    messageTemplateRepository.findAllForOrganisationId(UserContextHolder.getUserContext().getOrganisationId()) :
                    Collections.emptyList();
        } catch (GlificNotConfiguredException e) {
            return Collections.emptyList();
        }
    }
}
