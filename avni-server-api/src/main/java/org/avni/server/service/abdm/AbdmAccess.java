package org.avni.server.service.abdm;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abha.EkaEnvironments;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

// The one gate in front of ABDM record sharing, for the current user's organisation. Both switches come from one read of
// the organisation's settings row, and are read first, so an organisation that has not switched record sharing on costs
// that one read and no read of its Eka settings.
@Component
public class AbdmAccess {
    private final OrganisationConfigService organisationConfigService;
    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final EkaEnvironments ekaEnvironments;

    @Autowired
    public AbdmAccess(OrganisationConfigService organisationConfigService, ExternalSystemConfigRepository externalSystemConfigRepository,
                      EkaEnvironments ekaEnvironments) {
        this.organisationConfigService = organisationConfigService;
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.ekaEnvironments = ekaEnvironments;
    }

    // ABHA and record sharing switched on, and Eka settings for an environment this server allows, with a HIP ID, a
    // record-sharing client, a system user and a webhook signing key (missingForSharing).
    public boolean recordSharingOn() {
        return canShare(switchedOnSettings());
    }

    // For a named organisation, when there is no user (a webhook, a job): its own switches and Eka settings.
    public boolean recordSharingOn(long organisationId) {
        return sharing(organisationId).on();
    }

    // Whether both switches are on, and if so what the Eka settings lack for record sharing (null when nothing).
    public record Sharing(boolean switchedOn, String missing) {
        public boolean on() {
            return switchedOn && missing == null;
        }
    }

    // For a named organisation: a switch off is the organisation's choice, and its Eka settings are not read.
    public Sharing sharing(long organisationId) {
        if (!switchedOn(organisationId)) return new Sharing(false, null);
        return new Sharing(true, missingForSharing(ekaSettings(organisationId), ekaEnvironments));
    }

    // What keeps these Eka settings from record sharing on this server, named for the administrator; null when nothing.
    public static String missingForSharing(EkaSystemConfig settings, EkaEnvironments environments) {
        if (settings == null) return "Eka settings are not saved for this organisation";
        List<String> problems = new ArrayList<>();
        String environment = settings.getEnvironment();
        // A production server never shares through Eka's sandbox (avni.eka.allowedEnvironments).
        if (StringUtils.hasText(environment) && !environments.allows(environment)) {
            problems.add("Eka environment " + environment + " is not allowed on this server");
        }
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(environment)) missing.add("an Eka environment");
        if (settings.getHipIds().isEmpty()) missing.add("a HIP ID");
        // Not configured also when it is the M1 client, whose token the phone holds.
        if (!settings.isSharingClientConfigured()) missing.add("a record-sharing client, other than the ABHA client");
        if (!StringUtils.hasText(settings.getAvniSystemUser())) missing.add("a system user");
        if (!StringUtils.hasText(settings.getEncryptedWebhookSigningKey())) missing.add("a webhook signing key");
        if (!missing.isEmpty()) problems.add("The organisation's Eka settings lack " + String.join(", ", missing));
        return problems.isEmpty() ? null : String.join("; ", problems);
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

    // Without the system user every job fails, and without the signing key every webhook is refused.
    private boolean canShare(EkaSystemConfig settings) {
        return missingForSharing(settings, ekaEnvironments) == null;
    }

    private static Long currentOrganisationId() {
        UserContext userContext = UserContextHolder.getUserContext();
        return userContext == null ? null : userContext.getOrganisationId();
    }
}
