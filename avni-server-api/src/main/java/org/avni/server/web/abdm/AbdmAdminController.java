package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The admin page's Status tab: what record sharing needs and which of it the organisation has, and the address to give
// Eka for its webhooks. Answers even when record sharing is off, so the page can say what is missing. Organisation
// administrators only (EditOrganisationConfiguration).
@RestController
public class AbdmAdminController {
    private final AccessControlService accessControlService;
    private final AbdmConfig cfg;
    private final EkaAbdmClient ekaClient;
    private final OrganisationConfigService organisationConfigService;
    private final AbdmAccess access;
    private final AbdmSettings settings;

    @Autowired
    public AbdmAdminController(AccessControlService accessControlService, AbdmConfig cfg, EkaAbdmClient ekaClient,
                               OrganisationConfigService organisationConfigService, AbdmAccess access, AbdmSettings settings) {
        this.accessControlService = accessControlService;
        this.cfg = cfg;
        this.ekaClient = ekaClient;
        this.organisationConfigService = organisationConfigService;
        this.access = access;
        this.settings = settings;
    }

    @GetMapping("/web/abdm/status")
    public Map<String, Object> status() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        EkaSystemConfig eka = settings.find().orElse(null);
        boolean recordSharingOn = access.recordSharingOn();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("abhaEnabled", organisationConfigService.isAbhaEnabled());
        status.put("recordSharingEnabled", organisationConfigService.isAbdmRecordSharingEnabled());
        status.put("recordSharingOn", recordSharingOn);
        status.put("hiuOn", access.hiuOn());
        status.put("environment", eka == null ? null : eka.getEnvironment());
        status.put("hipIds", eka == null ? List.of() : eka.getHipIds());
        status.put("hiuId", eka == null ? null : eka.getHiuId());
        status.put("sharingClientConfigured", eka != null && eka.isSharingClientConfigured());
        status.put("webhookUrl", cfg.webhookUrl(eka == null ? null : eka.getWebhookId()));
        status.put("session", session(recordSharingOn));
        return status;
    }

    // Eka is asked only for an organisation whose record sharing is on.
    private Map<String, Object> session(boolean recordSharingOn) {
        Map<String, Object> session = new LinkedHashMap<>();
        if (!recordSharingOn) {
            session.put("ok", false);
            session.put("error", "Record sharing is not on for this organisation");
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
