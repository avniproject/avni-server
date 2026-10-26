package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.gateway.AbdmGatewayResponse;
import org.avni.server.service.abdm.gateway.AbdmSessionService;
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
// The bridge's own screen: is the session up, register our callback address with the gateway, what the gateway knows
// about us, and the message traffic. Organisation administrators only (EditOrganisationConfiguration).
// Gateway refusals come back as 200 with the error, so the screen can show them next to the button that caused them.
// In Eka mode the "session" is an Eka access token for the organisation, the gateway is Eka's base URL, and webhookUrl
// is the address to give Eka for its webhooks.
@RestController
public class AbdmAdminController {
    private final AccessControlService accessControlService;
    private final AbdmConfig cfg;
    private final AbdmSessionService session;
    private final AbdmGatewayClient client;
    private final AbdmMessageLog messageLog;
    private final EkaAbdmClient ekaClient;
    private final AbdmAutoLink autoLink;

    @Autowired
    public AbdmAdminController(AccessControlService accessControlService, AbdmConfig cfg, AbdmSessionService session,
                               AbdmGatewayClient client, AbdmMessageLog messageLog, EkaAbdmClient ekaClient, AbdmAutoLink autoLink) {
        this.accessControlService = accessControlService;
        this.cfg = cfg;
        this.session = session;
        this.client = client;
        this.messageLog = messageLog;
        this.ekaClient = ekaClient;
        this.autoLink = autoLink;
    }

    @GetMapping("/web/abdm/status")
    public Map<String, Object> status() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        Map<String, Object> sessionState = new LinkedHashMap<>();
        try {
            if (cfg.isEka()) ekaClient.checkToken();
            else session.accessToken();
            sessionState.put("ok", true);
            sessionState.put("error", null);
        } catch (RuntimeException e) {
            sessionState.put("ok", false);
            sessionState.put("error", e.getMessage());
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", cfg.isMock() ? "mock" : cfg.isEka() ? "eka" : "sandbox");
        status.put("gatewayBaseUrl", cfg.isEka() ? ekaBaseUrl() : cfg.gatewayBaseUrl());
        status.put("publicBaseUrl", cfg.publicBaseUrl());
        if (cfg.isEka()) status.put("webhookUrl", cfg.callbackUrl(AbdmPaths.IN_EKA_WEBHOOK));
        status.put("clientId", cfg.isEka() ? ekaClientId() : cfg.getClientId());
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

    @PostMapping("/web/abdm/bridge-url")
    public Map<String, Object> registerBridgeUrl() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        String url = cfg.publicBaseUrl();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            AbdmGatewayResponse response = client.patch(AbdmPaths.OUT_BRIDGE_URL, AbdmJson.obj().put("url", url));
            result.put("status", response.status());
            result.put("url", url);
        } catch (AbdmGatewayException e) {
            result.put("status", e.getStatus());
            result.put("url", url);
            result.put("error", e.getMessage());
            result.put("abdmCode", e.getAbdmCode());
        }
        return result;
    }

    @GetMapping("/web/abdm/bridge-services")
    public JsonNode bridgeServices() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        try {
            JsonNode body = client.get(AbdmPaths.OUT_BRIDGE_SERVICES).body();
            return body == null ? AbdmJson.arr() : body;
        } catch (AbdmGatewayException e) {
            ObjectNode error = AbdmJson.obj();
            error.put("error", e.getMessage());
            error.put("status", e.getStatus());
            error.put("abdmCode", e.getAbdmCode());
            return error;
        }
    }

    @GetMapping("/web/abdm/messages")
    public List<Map<String, Object>> messages(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return messageLog.recent(limit);
    }
}
