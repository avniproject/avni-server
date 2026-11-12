package org.avni.server.service.abdm;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// The one gate in front of ABDM record sharing, for the current user's organisation. Both switches come from one read of
// the organisation's settings row, and are read first, so an organisation that has not switched record sharing on costs
// that one read and no read of its Eka settings.
@Component
public class AbdmAccess {
    private final OrganisationConfigService organisationConfigService;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;

    @Autowired
    public AbdmAccess(OrganisationConfigService organisationConfigService, ExternalSystemConfigRepository externalSystemConfigRepository) {
        this.organisationConfigService = organisationConfigService;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
    }

    // ABHA and record sharing switched on, and Eka settings with a HIP ID, a record-sharing client, a system user and a
    // webhook signing key.
    public boolean recordSharingOn() {
        return canShare(switchedOnSettings());
    }

    // For a named organisation, when there is no user (a webhook, a job): its own switches and Eka settings.
    public boolean recordSharingOn(long organisationId) {
        if (!switchedOn(organisationId)) return false;
        return canShare(ekaSettings(organisationId));
    }

    // Record sharing on, and an HIU ID to receive records under.
    public boolean hiuOn() {
        EkaSystemConfig settings = switchedOnSettings();
        return canShare(settings) && StringUtils.hasText(settings.getHiuId());
    }

    // For the /web/abdm endpoints: an organisation without record sharing sees none of them.
    public void requireRecordSharingOn() {
        if (!recordSharingOn()) throw AbdmRequestException.notFound("Not found");
    }

    public void requireHiuOn() {
        if (!hiuOn()) throw AbdmRequestException.notFound("Not found");
    }

    private EkaSystemConfig switchedOnSettings() {
        Long organisationId = currentOrganisationId();
        if (organisationId == null || !switchedOn(organisationId)) return null;
        return ekaSettings(organisationId);
    }

    private boolean switchedOn(long organisationId) {
        OrganisationConfig config = organisationConfigService.getOrganisationConfigByOrgId(organisationId);
        return config != null && config.isFeatureEnabled(OrganisationConfigSettingKey.enableAbha.name())
                && config.isFeatureEnabled(OrganisationConfigSettingKey.enableAbdmRecordSharing.name());
    }

    private EkaSystemConfig ekaSettings(long organisationId) {
        try {
            return externalSystemConfigRepository.getEkaSystemConfig(organisationId);
        } catch (EkaNotConfiguredException e) {
            return null;
        }
    }

    // isSharingClientConfigured is false when the sharing client is the M1 client, whose token the phone holds. Without
    // the system user every job fails, and without the signing key every webhook is refused.
    private static boolean canShare(EkaSystemConfig settings) {
        return settings != null && !settings.getHipIds().isEmpty() && settings.isSharingClientConfigured()
                && StringUtils.hasText(settings.getAvniSystemUser()) && StringUtils.hasText(settings.getEncryptedWebhookSigningKey());
    }

    private static Long currentOrganisationId() {
        UserContext userContext = UserContextHolder.getUserContext();
        return userContext == null ? null : userContext.getOrganisationId();
    }
}
