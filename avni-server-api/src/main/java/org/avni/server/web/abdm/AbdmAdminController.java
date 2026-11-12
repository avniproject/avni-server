package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// The admin page's Status tab: is the organisation's Eka login up, and the address to give Eka for its webhooks.
// Organisation administrators only (EditOrganisationConfiguration).
@RestController
public class AbdmAdminController {
    private final AccessControlService accessControlService;
    private final AbdmConfig cfg;
    private final EkaAbdmClient ekaClient;

    @Autowired
    public AbdmAdminController(AccessControlService accessControlService, AbdmConfig cfg, EkaAbdmClient ekaClient) {
        this.accessControlService = accessControlService;
        this.cfg = cfg;
        this.ekaClient = ekaClient;
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
        return status;
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
}
