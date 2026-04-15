package org.avni.messaging.service;

import org.avni.messaging.config.WatiProperties;
import org.avni.messaging.domain.GlificSystemConfig;
import org.avni.messaging.domain.WatiSystemConfig;
import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.domain.exception.WatiNotConfiguredException;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.framework.security.AuthService;
import org.avni.server.service.OrganisationConfigService;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageSenderJob {
    private static final Logger logger = LoggerFactory.getLogger(MessagingService.class);
    private final MessagingService messagingService;
    private final OrganisationConfigService organisationConfigService;
    private final AuthService authService;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final WatiProperties watiProperties;
    @Value("${avni.messaging.scheduledSinceDays}")
    private String scheduledSinceDays;

    @Autowired
    public MessageSenderJob(MessagingService messagingService, OrganisationConfigService organisationConfigService,
                            AuthService authService, ExternalSystemConfigRepository externalSystemConfigRepository,
                            WatiProperties watiProperties) {
        this.messagingService = messagingService;
        this.organisationConfigService = organisationConfigService;
        this.authService = authService;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.watiProperties = watiProperties;
    }

    @Scheduled(fixedDelayString = "${avni.messagingScheduleMillis}")
    public void sendMessages() {
        logger.info("Job started");
        authService.authenticateByUserName("admin", null);
        List<OrganisationConfig> enabledOrganisations = organisationConfigService
                .findAllWithFeatureEnabled(OrganisationConfigSettingKey.enableMessaging.name());

        for (OrganisationConfig enabledOrganisation : enabledOrganisations) {
            sendMessages(enabledOrganisation);
            authService.authenticateByUserName("admin", null);
        }
        logger.info("Job ended");
    }

    private void sendMessages(OrganisationConfig enabledOrganisation) {
        try {
            String avniSystemUser = resolveAvniSystemUser(enabledOrganisation.getOrganisationId());
            if (avniSystemUser == null) {
                logger.warn("No messaging provider configured for org {}. Skipping.", enabledOrganisation.getOrganisationId());
                return;
            }
            authService.authenticateByUserName(avniSystemUser, null);
            Duration scheduledSince = Duration.standardDays(Long.parseLong(scheduledSinceDays));
            messagingService.sendMessages(scheduledSince);
        } catch (RuntimeException e) {
            logger.error("Message sending failed for organisation with id: {}. Check messaging provider config.", enabledOrganisation.getOrganisationId());
            logger.error("Exception for the above message sending failed error:", e);
        }
    }

    /**
     * Resolves the avniSystemUser for a given org from whichever messaging provider is configured.
     * Wati orgs are identified by their org ID being in avni.wati.orgIds (application.properties).
     * All other orgs fall back to Glific config.
     * Returns null if neither is configured — caller should skip the org.
     */
    private String resolveAvniSystemUser(Long organisationId) {
        if (watiProperties.getOrgIds().contains(organisationId)) {
            try {
                WatiSystemConfig watiConfig = externalSystemConfigRepository.getWatiSystemConfig(watiProperties.getPlatformOrgId());
                return watiConfig.getAvniSystemUser();
            } catch (WatiNotConfiguredException e) {
                logger.warn("Org {} is in Wati org list but Wati platform config is missing: {}", organisationId, e.getMessage());
                return null;
            }
        }
        try {
            GlificSystemConfig glificConfig = externalSystemConfigRepository.getGlificSystemConfig(organisationId);
            return glificConfig.getAvniSystemUser();
        } catch (GlificNotConfiguredException e) {
            logger.warn("Glific enabled but not configured for org {}: {}", organisationId, e.getMessage());
            return null;
        }
    }
}
