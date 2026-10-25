package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.mock.MockConsentManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// DEMO-ONLY abdm-hackathon: never merged. Only in the dev and test profiles, and only in mock mode.
// /mock-abdm/api/hiecm/** is the stand-in gateway our HIP and HIU call (unprotected, like the real one, which checks its
// own session token). /web/abdm/mock/** is the patient's side for the demo screen: approve or deny a consent request,
// and the PHR app's discover / link / confirm. Those need a logged-in user, nothing more.
@RestController
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "abdm.gateway.mode", havingValue = "mock", matchIfMissing = true)
public class MockConsentManagerController {
    private final MockConsentManager mock;

    @Autowired
    public MockConsentManagerController(MockConsentManager mock) {
        this.mock = mock;
    }

    @RequestMapping(value = AbdmConfig.MOCK_PREFIX + "/api/hiecm/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<JsonNode> gateway(HttpServletRequest request, @RequestHeader HttpHeaders headers,
                                            @RequestBody(required = false) JsonNode body) {
        String path = request.getRequestURI().substring(request.getContextPath().length() + AbdmConfig.MOCK_PREFIX.length());
        MockConsentManager.Answer answer = mock.handle(request.getMethod(), path, headers, body);
        return ResponseEntity.status(answer.status()).body(answer.body());
    }

    @GetMapping(AbdmConfig.MOCK_PREFIX + "/state")
    public JsonNode gatewayState() {
        return mock.state();
    }

    @PostMapping("/web/abdm/mock/consent-requests/{consentRequestId}/approve")
    public JsonNode approve(@PathVariable String consentRequestId) {
        return mock.approve(consentRequestId);
    }

    @PostMapping("/web/abdm/mock/consent-requests/{consentRequestId}/deny")
    public JsonNode deny(@PathVariable String consentRequestId) {
        return mock.deny(consentRequestId);
    }

    // {abhaAddress, hipId?, name?, gender?, yearOfBirth?} -> the HIP's on-discover body.
    @PostMapping("/web/abdm/mock/link/discover")
    public JsonNode discover(@RequestBody JsonNode body) {
        ObjectNode who = AbdmJson.obj();
        for (String field : List.of("name", "gender", "yearOfBirth")) {
            if (body.hasNonNull(field)) who.set(field, body.get(field));
        }
        return mock.phrDiscover(AbdmJson.text(body, "abhaAddress"), AbdmJson.text(body, "hipId"), who);
    }

    // {transactionId, abhaAddress, patientReference, careContextReferences:[...]} -> {linkReference}
    @PostMapping("/web/abdm/mock/link/init")
    public JsonNode linkInit(@RequestBody JsonNode body) {
        List<String> references = new ArrayList<>();
        JsonNode list = body.get("careContextReferences");
        if (list != null && list.isArray()) list.forEach(reference -> references.add(reference.asText()));
        String linkReference = mock.phrLinkInit(AbdmJson.text(body, "transactionId"), AbdmJson.text(body, "abhaAddress"),
                AbdmJson.text(body, "patientReference"), references);
        return AbdmJson.obj().put("linkReference", linkReference);
    }

    // {linkReference, otp} -> {status, patient | error}
    @PostMapping("/web/abdm/mock/link/confirm")
    public JsonNode linkConfirm(@RequestBody JsonNode body) {
        return mock.phrLinkConfirm(AbdmJson.text(body, "linkReference"), AbdmJson.text(body, "otp"));
    }

    @GetMapping("/web/abdm/mock/state")
    public JsonNode state() {
        return mock.state();
    }
}
