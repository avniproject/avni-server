package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.hip.AbdmAutoLink;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// The admin page's Status and Traffic tabs: is the organisation's Eka login up, the address to give Eka for its webhooks,
// linking on save, and the message traffic. Organisation administrators only (EditOrganisationConfiguration).
@RestController
public class AbdmAdminController {
    private final AccessControlService accessControlService;
    private final AbdmConfig cfg;
    private final AbdmMessageLog messageLog;
    private final EkaAbdmClient ekaClient;
    private final AbdmAutoLink autoLink;

    @Autowired
    public AbdmAdminController(AccessControlService accessControlService, AbdmConfig cfg, AbdmMessageLog messageLog,
                               EkaAbdmClient ekaClient, AbdmAutoLink autoLink) {
        this.accessControlService = accessControlService;
        this.cfg = cfg;
        this.messageLog = messageLog;
        this.ekaClient = ekaClient;
        this.autoLink = autoLink;
    }

    @GetMapping("/web/abdm/status")
    public Map<String, Object> status() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        Map<String, Object> sessionState = new LinkedHashMap<>();
        try {
            ekaClient.checkToken();
            sessionState.put("ok", true);
            sessionState.put("error", null);
        } catch (RuntimeException e) {
            sessionState.put("ok", false);
            sessionState.put("error", e.getMessage());
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("ekaBaseUrl", ekaBaseUrl());
        status.put("publicBaseUrl", cfg.publicBaseUrl());
        status.put("webhookUrl", cfg.callbackUrl(AbdmPaths.IN_EKA_WEBHOOK));
        status.put("clientId", ekaClientId());
        status.put("hipId", cfg.getHipId());
        status.put("hiuId", cfg.getHiuId());
        status.put("session", sessionState);
        status.put("autoLink", autoLink.isOn());
        return status;
    }

    @PostMapping("/web/abdm/auto-link")
    public Map<String, Object> setAutoLink(@RequestBody Map<String, Object> body) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        autoLink.set(Boolean.TRUE.equals(body.get("on")));
        return Map.of("autoLink", autoLink.isOn());
    }

    private String ekaClientId() {
        try {
            return ekaClient.clientId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String ekaBaseUrl() {
        try {
            return ekaClient.baseUrl();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @GetMapping("/web/abdm/messages")
    public List<Map<String, Object>> messages(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return messageLog.recent(limit);
    }
}
