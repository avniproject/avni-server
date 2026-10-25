package org.avni.server.service.abdm.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

// DEMO-ONLY abdm-hackathon: never merged.
// M4: search the facility (HFR) and professional (HPR) registries, and submit a facility for registration.
// Sandbox mode posts to the configured registry URLs and hands back whatever the registry answered, refusals included,
// so the demo shows the real answer. Mock mode answers from canned JSON under abdm/mock/.
// Eka mode goes through Eka Care's ABDM Connect (spec section 4): the facility search looks one HFR ID up, the
// professional search checks one HPR ID, and "submit facility" onboards our HIP at Eka (hip_code, scan_share_url).
// DEMO-ONLY / unverified paths: the registry paths and request/response shapes are not checked against the sandbox.
// Never logs a request or response body.
@Component
public class AbdmRegistryService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmRegistryService.class);
    private static final String MOCK_DIR = "/abdm/mock/";
    static final String EKA_HFR_SEARCH = "/abdm/nhpr/v1/hfr/search/";
    static final String EKA_HPR_CHECK = "/abdm/nhpr/v1/hpr/aadhaar/check/available";
    static final String EKA_HIP_ONBOARD = "/abdm/v1/hip/onboard";

    private final AbdmConfig config;
    private final AbdmGatewayClient gatewayClient;
    private final EkaAbdmClient ekaClient;

    @Autowired
    public AbdmRegistryService(AbdmConfig config, AbdmGatewayClient gatewayClient, EkaAbdmClient ekaClient) {
        this.config = config;
        this.gatewayClient = gatewayClient;
        this.ekaClient = ekaClient;
    }

    public JsonNode searchFacilities(JsonNode criteria) {
        if (config.isEka()) return ekaFacilitySearch(criteria);
        if (config.isMock()) {
            JsonNode canned = canned("hfr-search.json");
            return mock(filter(canned, "facilities", "totalFacilities", criteria, "facilityName", "facilityId"));
        }
        return sandbox("HFR search", config.getHfrBaseUrl(), config.getHfrSearchPath(), "abdm.m4.hfr-search-path", criteria);
    }

    public JsonNode searchProfessionals(JsonNode criteria) {
        if (config.isEka()) return ekaProfessionalCheck(criteria);
        if (config.isMock()) {
            JsonNode canned = canned("hpr-search.json");
            return mock(filter(canned, "professionals", "totalProfessionals", criteria, "name", "hprId"));
        }
        return sandbox("HPR search", config.getHprBaseUrl(), config.getHprSearchPath(), "abdm.m4.hpr-search-path", criteria);
    }

    public JsonNode submitFacility(JsonNode facility) {
        if (config.isEka()) return ekaOnboard(facility);
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

    // ---- Eka mode

    // GET /abdm/nhpr/v1/hfr/search/{hfrId}: one facility, by its HFR ID only.
    private JsonNode ekaFacilitySearch(JsonNode criteria) {
        String hfrId = criteria == null ? null : firstText(criteria, "facilityId", "hfrId", "hfr_id", "id");
        if (!StringUtils.hasText(hfrId)) {
            return ekaError("Eka searches facilities by HFR ID: enter the facility's HFR ID in facilityId");
        }
        String path = EKA_HFR_SEARCH + URLEncoder.encode(hfrId.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        return eka("HFR search", () -> ekaClient.get(path, Map.of(EkaAbdmClient.H_HIP_ID, config.getHipId())), body -> {
            JsonNode data = body.get("data");
            if (data == null || !data.isObject()) return body;
            ObjectNode results = AbdmJson.obj();
            results.putArray("facilities").add(data);
            results.put("totalFacilities", 1);
            return results;
        });
    }

    // Eka has no professional search. Its nearest call says whether an HPR ID is free to claim: not free means taken.
    private JsonNode ekaProfessionalCheck(JsonNode criteria) {
        String hprId = criteria == null ? null : firstText(criteria, "hprId", "hpr_id", "id");
        if (!StringUtils.hasText(hprId)) {
            return ekaError("Professional search is not available via Eka: enter an HPR ID to check whether it is registered");
        }
        ObjectNode body = AbdmJson.obj();
        body.put("hprId", hprId.trim());
        return eka("HPR check", () -> ekaClient.post(EKA_HPR_CHECK, body, Map.of(EkaAbdmClient.H_HIP_ID, config.getHipId())), answer -> {
            JsonNode available = AbdmJson.at(answer, "data.available");
            if (available == null || !available.isBoolean()) available = answer.get("available");
            if (available == null || !available.isBoolean()) return answer;
            ObjectNode results = AbdmJson.obj();
            ObjectNode professional = results.putArray("professionals").addObject();
            professional.put("hprId", hprId.trim());
            professional.put("available", available.asBoolean());
            professional.put("registered", !available.asBoolean());
            results.put("note", "Eka checks whether an HPR ID is taken; it has no professional search");
            return results;
        });
    }

    // POST /abdm/v1/hip/onboard: registers our HIP with Eka. Answers hip_id, hip_name, hip_code, scan_share_url.
    private JsonNode ekaOnboard(JsonNode facility) {
        ObjectNode body = AbdmJson.obj();
        String hipId = facility == null ? null : firstText(facility, "hipId", "hip_id");
        String name = facility == null ? null : firstText(facility, "facilityName", "name");
        body.put("hip_id", StringUtils.hasText(hipId) ? hipId.trim() : config.getHipId());
        body.put("name", StringUtils.hasText(name) ? name.trim() : config.getHipName());
        if (StringUtils.hasText(config.getEkaClinicId())) body.put("clinic_id", config.getEkaClinicId());
        return eka("HIP onboard", () -> ekaClient.post(EKA_HIP_ONBOARD, body, Map.of()), answer -> {
            JsonNode data = answer.get("data");
            return data != null && data.isObject() ? data : answer;
        });
    }

    // {mode:"eka", status, results} or {mode:"eka", error, status, abdmCode}. Eka's M4 envelope can say
    // {"success": false, "error": {code, message}} with a 2xx; that is an error too.
    private JsonNode eka(String call, Supplier<AbdmGatewayResponse> request, Function<JsonNode, JsonNode> results) {
        ObjectNode answer = AbdmJson.obj();
        answer.put("mode", "eka");
        try {
            AbdmGatewayResponse response = request.get();
            logger.info("ABDM M4 Eka {}: status {}", call, response.status());
            answer.put("status", response.status());
            JsonNode body = response.body() == null || response.body().isNull() ? AbdmJson.obj() : response.body();
            JsonNode success = body.get("success");
            if (success != null && success.isBoolean() && !success.asBoolean()) {
                String code = AbdmJson.text(body, "error.code");
                String message = AbdmJson.text(body, "error.message");
                answer.put("error", message != null ? message : call + " failed at Eka");
                if (code != null) answer.put("abdmCode", code);
                return answer;
            }
            answer.set("results", body.isObject() ? results.apply(body) : body);
        } catch (AbdmGatewayException e) {
            logger.warn("ABDM M4 Eka {} refused: status {} code {}", call, e.getStatus(), e.getAbdmCode());
            answer.put("error", e.getMessage() == null ? call + " failed" : e.getMessage());
            answer.put("status", e.getStatus());
            if (e.getAbdmCode() != null) answer.put("abdmCode", e.getAbdmCode());
        }
        return answer;
    }

    private static ObjectNode ekaError(String message) {
        ObjectNode answer = AbdmJson.obj();
        answer.put("mode", "eka");
        answer.put("error", message);
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
