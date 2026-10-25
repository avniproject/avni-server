package org.avni.server.service.abdm.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.gateway.AbdmGatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// DEMO-ONLY abdm-hackathon: never merged.
// M4: search the facility (HFR) and professional (HPR) registries, and submit a facility for registration.
// Sandbox mode posts to the configured registry URLs and hands back whatever the registry answered, refusals included,
// so the demo shows the real answer. Mock mode answers from canned JSON under abdm/mock/.
// DEMO-ONLY / unverified paths: the registry paths and request/response shapes are not checked against the sandbox.
// Never logs a request or response body.
@Component
public class AbdmRegistryService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmRegistryService.class);
    private static final String MOCK_DIR = "/abdm/mock/";

    private final AbdmConfig config;
    private final AbdmGatewayClient gatewayClient;

    @Autowired
    public AbdmRegistryService(AbdmConfig config, AbdmGatewayClient gatewayClient) {
        this.config = config;
        this.gatewayClient = gatewayClient;
    }

    public JsonNode searchFacilities(JsonNode criteria) {
        if (config.isMock()) {
            JsonNode canned = canned("hfr-search.json");
            return mock(filter(canned, "facilities", "totalFacilities", criteria, "facilityName", "facilityId"));
        }
        return sandbox("HFR search", config.getHfrBaseUrl(), config.getHfrSearchPath(), "abdm.m4.hfr-search-path", criteria);
    }

    public JsonNode searchProfessionals(JsonNode criteria) {
        if (config.isMock()) {
            JsonNode canned = canned("hpr-search.json");
            return mock(filter(canned, "professionals", "totalProfessionals", criteria, "name", "hprId"));
        }
        return sandbox("HPR search", config.getHprBaseUrl(), config.getHprSearchPath(), "abdm.m4.hpr-search-path", criteria);
    }

    public JsonNode submitFacility(JsonNode facility) {
        if (config.isMock()) {
            ObjectNode receipt = (ObjectNode) canned("hfr-submit.json");
            receipt.put("trackingId", "MOCK-" + UUID.randomUUID());
            String name = facility == null ? null : AbdmJson.text(facility, "facilityName");
            if (StringUtils.hasText(name)) receipt.put("facilityName", name);
            return mock(receipt);
        }
        return sandbox("HFR submit", config.getHfrBaseUrl(), config.getHfrSubmitPath(), "abdm.m4.hfr-submit-path", facility);
    }

    private JsonNode sandbox(String call, String baseUrl, String path, String pathProperty, JsonNode body) {
        ObjectNode answer = AbdmJson.obj();
        answer.put("mode", "sandbox");
        if (!StringUtils.hasText(path)) {
            answer.put("error", call + " path not configured (" + pathProperty + ")");
            return answer;
        }
        try {
            AbdmGatewayResponse response = gatewayClient.postAbsolute(baseUrl + path, body == null ? AbdmJson.obj() : body, Map.of());
            logger.info("ABDM M4 {}: status {}", call, response.status());
            answer.put("status", response.status());
            answer.set("results", response.body() == null || response.body().isNull() ? AbdmJson.obj() : response.body());
        } catch (AbdmGatewayException e) {
            logger.warn("ABDM M4 {} refused: status {} code {}", call, e.getStatus(), e.getAbdmCode());
            answer.put("error", e.getMessage() == null ? call + " failed" : e.getMessage());
            answer.put("status", e.getStatus());
            if (e.getAbdmCode() != null) answer.put("abdmCode", e.getAbdmCode());
        }
        return answer;
    }

    private static ObjectNode mock(JsonNode results) {
        ObjectNode answer = AbdmJson.obj();
        answer.put("mode", "mock");
        answer.set("results", results);
        return answer;
    }

    // DEMO-ONLY: the canned list narrowed by a name (contains, any case) and an id (exact), when the criteria carry them.
    private static JsonNode filter(JsonNode canned, String listField, String totalField, JsonNode criteria, String nameField, String idField) {
        String name = criteria == null ? null : firstText(criteria, nameField, "name");
        String id = criteria == null ? null : AbdmJson.text(criteria, idField);
        if (!StringUtils.hasText(name) && !StringUtils.hasText(id)) return canned;

        ArrayNode kept = AbdmJson.arr();
        for (JsonNode row : canned.path(listField)) {
            boolean nameMatches = !StringUtils.hasText(name)
                    || row.path(nameField).asText("").toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT));
            boolean idMatches = !StringUtils.hasText(id) || id.trim().equals(row.path(idField).asText(""));
            if (nameMatches && idMatches) kept.add(row);
        }
        ObjectNode narrowed = ((ObjectNode) canned).deepCopy();
        narrowed.set(listField, kept);
        narrowed.put(totalField, kept.size());
        return narrowed;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = AbdmJson.text(node, field);
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private static JsonNode canned(String file) {
        try (InputStream in = AbdmRegistryService.class.getResourceAsStream(MOCK_DIR + file)) {
            if (in == null) throw new IllegalStateException("ABDM mock file missing: " + file);
            return AbdmJson.M.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("ABDM mock file not read: " + file, e);
        }
    }
}
