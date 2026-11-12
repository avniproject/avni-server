package org.avni.server.service.abdm;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// The one gate in front of ABDM record sharing, for the current user's organisation. The switches are read first, so an
// organisation that has not switched record sharing on costs no read of its Eka settings.
@Component
public class AbdmAccess {
    private final OrganisationConfigService organisationConfigService;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;

    @Autowired
    public AbdmAccess(OrganisationConfigService organisationConfigService, ExternalSystemConfigRepository externalSystemConfigRepository) {
        this.organisationConfigService = organisationConfigService;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
    }

    // ABHA and record sharing switched on, and Eka settings with a HIP ID and a record-sharing client.
    public boolean recordSharingOn() {
        return canShare(switchedOnSettings());
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
        if (organisationId == null) return null;
        if (!organisationConfigService.isAbhaEnabled() || !organisationConfigService.isAbdmRecordSharingEnabled()) return null;
        try {
            return externalSystemConfigRepository.getEkaSystemConfig(organisationId);
        } catch (EkaNotConfiguredException e) {
            return null;
        }
    }

    private static boolean canShare(EkaSystemConfig settings) {
        return settings != null && !settings.getHipIds().isEmpty() && settings.isSharingClientConfigured();
    }

    private static Long currentOrganisationId() {
        UserContext userContext = UserContextHolder.getUserContext();
        return userContext == null ? null : userContext.getOrganisationId();
    }
}
