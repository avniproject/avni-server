package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJobRuns;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The admin page's Status tab: what record sharing needs and which of it the organisation has, the address to give Eka
// for its webhooks, how each ABDM job last went for it, its visits by link status (queue), and its shares that ended
// FAILED or that Eka never answered within the traffic retention (failedShares, unansweredShares). Answers even when
// record sharing is off, so the page can say
// what is missing. The user's own organisation only. Organisation administrators only (EditOrganisationConfiguration).
@RestController
public class AbdmAdminController {
    private final AccessControlService accessControlService;
    private final AbdmConfig cfg;
    private final EkaAbdmClient ekaClient;
    private final OrganisationConfigService organisationConfigService;
    private final AbdmAccess access;
    private final AbdmSettings settings;
    private final AbdmJobRuns jobRuns;
    private final AbdmOrgContext orgContext;
    private final AbdmHipStore hipStore;

    @Autowired
    public AbdmAdminController(AccessControlService accessControlService, AbdmConfig cfg, EkaAbdmClient ekaClient,
                               OrganisationConfigService organisationConfigService, AbdmAccess access, AbdmSettings settings,
                               AbdmJobRuns jobRuns, AbdmOrgContext orgContext, AbdmHipStore hipStore) {
        this.accessControlService = accessControlService;
        this.cfg = cfg;
        this.ekaClient = ekaClient;
        this.organisationConfigService = organisationConfigService;
        this.access = access;
        this.settings = settings;
        this.jobRuns = jobRuns;
        this.orgContext = orgContext;
        this.hipStore = hipStore;
    }

    @GetMapping("/web/abdm/status")
    public Map<String, Object> status() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        EkaSystemConfig eka = settings.find().orElse(null);
        boolean recordSharingOn = access.recordSharingOn();
        boolean abhaEnabled = organisationConfigService.isAbhaEnabled();
        boolean recordSharingEnabled = organisationConfigService.isAbdmRecordSharingEnabled();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("abhaEnabled", abhaEnabled);
        status.put("recordSharingEnabled", recordSharingEnabled);
        status.put("recordSharingOn", recordSharingOn);
        status.put("hiuOn", access.hiuOn());
        status.put("environment", eka == null ? null : eka.getEnvironment());
        status.put("hipIds", eka == null ? List.of() : eka.getHipIds());
        status.put("hiuId", eka == null ? null : eka.getHiuId());
        status.put("sharingClientConfigured", eka != null && eka.isSharingClientConfigured());
        status.put("webhookUrl", cfg.webhookUrl(eka == null ? null : eka.getWebhookId()));
        status.put("session", session(recordSharingOn, abhaEnabled && recordSharingEnabled, eka));
        Long organisationId = orgContext.organisationId();
        status.put("jobs", organisationId == null ? Map.of() : jobRuns.of(organisationId));
        status.put("queue", organisationId == null ? Map.of() : hipStore.linkQueueCounts());
        Instant since = Instant.now().minus(cfg.messageRetention());
        status.put("failedShares", organisationId == null ? 0L : hipStore.failedSharesSince(since));
        status.put("unansweredShares", organisationId == null ? 0L : hipStore.unansweredSharesSince(since));
        return status;
    }

    // Eka is asked only for an organisation whose record sharing is on. With both switches on and sharing still off, the
    // error names the Eka settings it lacks.
    private Map<String, Object> session(boolean recordSharingOn, boolean switchedOn, EkaSystemConfig eka) {
        Map<String, Object> session = new LinkedHashMap<>();
        if (!recordSharingOn) {
            String missing = switchedOn ? AbdmAccess.missingForSharing(eka) : null;
            session.put("ok", false);
            session.put("error", missing != null ? missing : "Record sharing is not on for this organisation");
            return session;
        }
        try {
            ekaClient.checkToken();
            session.put("ok", true);
            session.put("error", null);
        } catch (RuntimeException e) {
            session.put("ok", false);
            session.put("error", e.getMessage());
        }
        return session;
    }
}
