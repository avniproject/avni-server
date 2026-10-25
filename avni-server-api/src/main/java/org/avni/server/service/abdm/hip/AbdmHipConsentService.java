package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// DEMO-ONLY abdm-hackathon: never merged.
// The HIP side of consent: the gateway tells us of a consent granted (or revoked, expired) over our care contexts; we keep
// the artefact and acknowledge it.
@Service
public class AbdmHipConsentService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHipConsentService.class);

    public static final String GRANTED = "GRANTED";

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbdmGatewayClient gateway;

    public AbdmHipConsentService(AbdmConfig cfg, AbdmHipStore store, AbdmGatewayClient gateway) {
        this.cfg = cfg;
        this.store = store;
        this.gateway = gateway;
    }

    public void consentNotify(JsonNode body, AbdmCallbackHeaders headers) {
        JsonNode notification = AbdmJson.at(body, "notification");
        String status = AbdmJson.text(notification, "status");
        JsonNode detail = AbdmJson.at(notification, "consentDetail");
        boolean hasDetail = detail != null && detail.isObject();
        String consentId = AbdmFlows.firstNonNull(AbdmJson.text(notification, "consentId"),
                hasDetail ? AbdmJson.text(detail, "consentId") : null);

        ObjectNode reply = AbdmJson.obj();
        if (consentId == null) {
            logger.warn("ABDM HIP consent notify without a consent id; refused");
            reply.set("error", AbdmFlows.error("CONSENT_ID_MISSING", "No consent id in the notification"));
        } else {
            if (hasDetail) {
                store.upsertConsent(consentId, AbdmJson.text(detail, "patient.id"),
                        AbdmFlows.firstNonNull(AbdmJson.text(detail, "hip.id"), cfg.getHipId()), AbdmJson.text(detail, "hiu.id"),
                        AbdmJson.text(detail, "purpose.code"), String.join(",", AbdmFlows.texts(AbdmJson.at(detail, "hiTypes"), null)),
                        AbdmTime.parse(AbdmJson.text(detail, "permission.dateRange.from")),
                        AbdmTime.parse(AbdmJson.text(detail, "permission.dateRange.to")),
                        AbdmTime.parse(AbdmJson.text(detail, "permission.dataEraseAt")),
                        status == null ? GRANTED : status, detail);
            } else if (store.setConsentStatus(consentId, status) == 0) {
                store.upsertConsent(consentId, null, cfg.getHipId(), null, null, null, null, null, null, status, null);
            }
            logger.info("ABDM HIP consent {} is {}", consentId, status);
            ObjectNode ack = reply.putObject("acknowledgement");
            ack.put("status", "ok");
            ack.put("consentId", consentId);
        }
        reply.set("response", AbdmFlows.responseTo(headers.requestId()));
        try {
            gateway.post(AbdmPaths.OUT_HIP_CONSENT_ON_NOTIFY, AbdmGatewayClient.newRequestId(), reply,
                    Map.of(AbdmPaths.H_HIP_ID, cfg.getHipId()));
        } catch (RuntimeException e) {
            logger.warn("ABDM HIP consent on-notify for {} failed: {}", consentId, e.getMessage());
        }
    }

    // [{consentId, abhaAddress, hiuId, status, hiTypes, dateFrom, dateTo, careContexts, dataFlows:[{transactionId, status, entries}], updatedAt}]
    public List<Map<String, Object>> list() {
        List<AbdmHipStore.Consent> consents = store.listConsents();
        Map<String, List<AbdmHipStore.DataFlow>> flows = store.findDataFlows(consents.stream().map(AbdmHipStore.Consent::consentId)
                        .filter(id -> id != null).collect(Collectors.toList()))
                .stream().collect(Collectors.groupingBy(AbdmHipStore.DataFlow::consentId));
        Map<String, String> displays = new HashMap<>();
        List<String> allRefs = consents.stream().flatMap(c -> careContextRefs(c.consentDetail()).stream()).distinct().collect(Collectors.toList());
        for (AbdmHipStore.CareContext cc : store.findCareContextsByReferences(cfg.getHipId(), allRefs)) displays.put(cc.reference(), cc.display());

        List<Map<String, Object>> out = new ArrayList<>();
        for (AbdmHipStore.Consent c : consents) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("consentId", c.consentId());
            m.put("abhaAddress", c.abhaAddress());
            m.put("hiuId", c.hiuId());
            m.put("status", c.status());
            m.put("hiTypes", c.hiTypes() == null || c.hiTypes().isBlank() ? List.of() : Arrays.asList(c.hiTypes().split(",")));
            m.put("dateFrom", AbdmFlows.iso(c.dateFrom()));
            m.put("dateTo", AbdmFlows.iso(c.dateTo()));
            // The visit's display where we know it, else its reference.
            m.put("careContexts", careContextRefs(c.consentDetail()).stream().map(r -> displays.getOrDefault(r, r)).collect(Collectors.toList()));
            List<Map<String, Object>> dataFlows = new ArrayList<>();
            for (AbdmHipStore.DataFlow f : flows.getOrDefault(c.consentId(), List.of())) {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("transactionId", f.transactionId());
                fm.put("status", f.status());
                fm.put("entries", f.entries());
                dataFlows.add(fm);
            }
            m.put("dataFlows", dataFlows);
            m.put("updatedAt", AbdmFlows.iso(c.updatedAt()));
            out.add(m);
        }
        return out;
    }

    // The encounter uuids a consent covers.
    public static List<String> careContextRefs(JsonNode consentDetail) {
        return AbdmFlows.texts(AbdmJson.at(consentDetail, "careContexts"), "careContextReference");
    }
}
