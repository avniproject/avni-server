package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.crypto.FideliusCrypto;
import org.avni.server.service.abdm.crypto.FideliusKeyMaterial;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hip.AbdmRetry;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// DEMO-ONLY abdm-hackathon: never merged.
// Avni as HIU, the records half: request health information under a fetched consent with a fresh key pair, learn the
// transaction id, then take the HIP's encrypted push, decrypt each entry and keep the FHIR bundle.
@Service
public class AbdmHiuDataService {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHiuDataService.class);

    private final AbdmConfig cfg;
    private final AbdmHiuStore store;
    private final AbdmGatewayClient gateway;
    private final FideliusCrypto crypto;
    private final EkaCredentialCipher cipher;
    private final AbdmRetry retry;

    public AbdmHiuDataService(AbdmConfig cfg, AbdmHiuStore store, AbdmGatewayClient gateway, FideliusCrypto crypto,
                              EkaCredentialCipher cipher, AbdmRetry retry) {
        this.cfg = cfg;
        this.store = store;
        this.gateway = gateway;
        this.crypto = crypto;
        this.cipher = cipher;
        this.retry = retry;
    }

    public void requestHealthInformation(String consentId) {
        AbdmHiuStore.Consent artefact = store.findArtefact(consentId).orElse(null);
        FideliusKeyMaterial keys = crypto.generateKeyMaterial();
        Instant keyExpiry = Instant.now().plus(Duration.ofHours(1));
        String requestId = AbdmGatewayClient.newRequestId();
        // Persist before send: on-request carries this REQUEST-ID back. DEMO-ONLY reuse of the Eka key for the private key.
        long flowId = store.insertDataFlow(consentId, requestId, cipher.encrypt(keys.privateKeyBase64()), keys.publicKeyX509Base64(),
                keys.nonceBase64(), keyExpiry, "REQUESTED");

        ObjectNode body = AbdmJson.obj();
        ObjectNode hiRequest = body.putObject("hiRequest");
        hiRequest.putObject("consent").put("id", consentId);
        hiRequest.set("dateRange", dateRange(artefact));
        hiRequest.put("dataPushUrl", cfg.dataPushUrl());
        hiRequest.set("keyMaterial", AbdmFlows.keyMaterial(keys.publicKeyX509Base64(), keys.nonceBase64(), keyExpiry));
        try {
            gateway.post(AbdmPaths.OUT_HI_REQUEST, requestId, body, Map.of(AbdmPaths.H_HIU_ID, cfg.getHiuId()));
        } catch (RuntimeException e) {
            store.finishDataFlow(flowId, "FAILED", 0, "Health information request failed: " + e.getMessage());
            logger.warn("ABDM health information request for consent {} failed: {}", consentId, e.getMessage());
        }
    }

    // The consented range, else a wide default.
    private static ObjectNode dateRange(AbdmHiuStore.Consent artefact) {
        ObjectNode range = AbdmJson.obj();
        JsonNode fromDetail = artefact == null ? null : AbdmJson.at(artefact.consentDetail(), "permission.dateRange");
        String from = AbdmJson.text(fromDetail, "from");
        String to = AbdmJson.text(fromDetail, "to");
        if (from == null && artefact != null) from = AbdmFlows.iso(artefact.dateFrom());
        if (to == null && artefact != null) to = AbdmFlows.iso(artefact.dateTo());
        range.put("from", from != null ? from : AbdmTime.format(Instant.now().minus(3650, ChronoUnit.DAYS)));
        range.put("to", to != null ? to : AbdmTime.now());
        return range;
    }

    public void onRequest(JsonNode body, AbdmCallbackHeaders headers) {
        String requestId = AbdmJson.requestIdOf(body);
        Optional<AbdmHiuStore.DataFlow> found = retry.find(() -> store.findDataFlowByRequest(requestId));
        if (found.isEmpty()) {
            logger.warn("ABDM health information on-request for unknown request {}; dropped", requestId);
            return;
        }
        String error = AbdmFlows.errorOf(body);
        String transactionId = AbdmJson.text(body, "hiRequest.transactionId");
        String sessionStatus = AbdmJson.text(body, "hiRequest.sessionStatus");
        if (error != null) {
            store.setTransaction(found.get().id(), transactionId, "FAILED", error);
        } else {
            store.setTransaction(found.get().id(), transactionId, sessionStatus == null ? "ACKNOWLEDGED" : sessionStatus, null);
        }
    }

    public void dataPush(JsonNode body, AbdmCallbackHeaders headers) {
        String transactionId = AbdmJson.text(body, "transactionId");
        // The push can overtake on-request, which is what tells us the transaction id.
        Optional<AbdmHiuStore.DataFlow> found = retry.find(() -> store.findDataFlowByTransaction(transactionId));
        if (found.isEmpty()) {
            logger.warn("ABDM data push for unknown transaction {}; dropped", transactionId);
            return;
        }
        AbdmHiuStore.DataFlow flow = found.get();
        String hipId = store.findArtefact(flow.consentId()).map(AbdmHiuStore.Consent::hipId).orElse(headers == null ? null : headers.hipId());
        String senderKey = AbdmJson.text(body, "keyMaterial.dhPublicKey.keyValue");
        String senderNonce = AbdmJson.text(body, "keyMaterial.nonce");
        String privateKey = null;
        try {
            privateKey = cipher.decrypt(flow.encryptedPrivateKey());
        } catch (RuntimeException e) {
            logger.error("ABDM data push {}: our private key could not be read", transactionId);
        }

        ArrayNode statusResponses = AbdmJson.arr();
        int received = 0;
        JsonNode entries = AbdmJson.at(body, "entries");
        if (entries != null && entries.isArray()) {
            for (JsonNode entry : entries) {
                String reference = AbdmJson.text(entry, "careContextReference");
                String content = AbdmJson.text(entry, "content");
                String problem = null;
                if (content == null) {
                    problem = "No inline content (links not supported in the demo)";
                } else if (privateKey == null || senderKey == null || senderNonce == null) {
                    problem = "Key material missing";
                } else {
                    try {
                        String plaintext = crypto.decrypt(content, privateKey, flow.nonce(), senderKey, senderNonce);
                        JsonNode bundle = AbdmJson.read(plaintext);
                        if (bundle == null || !bundle.isObject()) throw new IllegalArgumentException("not a JSON object");
                        String checksum = AbdmJson.text(entry, "checksum");
                        Boolean matched = checksum == null ? null : AbdmFlows.md5Hex(plaintext).equalsIgnoreCase(checksum);
                        if (Boolean.FALSE.equals(matched)) {
                            // DEMO-ONLY: logged, not enforced; ABDM's checksum algorithm is unverified.
                            logger.warn("ABDM data push {}: checksum mismatch for care context {}", transactionId, reference);
                        }
                        store.insertHealthInformation(flow.id(), transactionId, flow.consentId(), hipId, reference, matched,
                                AbdmJson.write(bundle));
                        received++;
                    } catch (RuntimeException e) {
                        problem = "Could not decrypt or read the record";
                        logger.warn("ABDM data push {}: entry for care context {} unreadable: {}", transactionId, reference,
                                e.getClass().getSimpleName());
                    }
                }
                ObjectNode s = statusResponses.addObject();
                s.put("careContextReference", reference);
                s.put("hiStatus", problem == null ? "OK" : "ERRORED");
                s.put("description", problem == null ? "Received" : problem);
            }
        }

        String sessionStatus = received > 0 ? "TRANSFERRED" : "FAILED";
        store.finishDataFlow(flow.id(), received > 0 ? "RECEIVED" : "FAILED", received,
                received > 0 ? null : "No record could be read");
        notifyGateway(flow.consentId(), transactionId, sessionStatus, hipId, statusResponses);
    }

    private void notifyGateway(String consentId, String transactionId, String sessionStatus, String hipId, ArrayNode statusResponses) {
        ObjectNode body = AbdmJson.obj();
        ObjectNode n = body.putObject("notification");
        n.put("consentId", consentId);
        n.put("transactionId", transactionId);
        n.put("doneAt", AbdmTime.now());
        ObjectNode notifier = n.putObject("notifier");
        notifier.put("type", "HIU");
        notifier.put("id", cfg.getHiuId());
        ObjectNode status = n.putObject("statusNotification");
        status.put("sessionStatus", sessionStatus);
        status.put("hipId", hipId);
        status.set("statusResponses", statusResponses);
        try {
            gateway.post(AbdmPaths.OUT_HI_NOTIFY, AbdmGatewayClient.newRequestId(), body, Map.of(AbdmPaths.H_HIU_ID, cfg.getHiuId()));
        } catch (RuntimeException e) {
            logger.warn("ABDM HIU health information notify for {} failed: {}", transactionId, e.getMessage());
        }
    }

    // [{careContextReference, hipId, transactionId, receivedAt, checksumMatched, bundle}]
    public List<Map<String, Object>> records(String consentRequestUuid) {
        AbdmHiuStore.Consent request = store.findConsentRequestByUuid(consentRequestUuid)
                .orElseThrow(() -> AbdmRequestException.notFound("Consent request not found"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AbdmHiuStore.Record r : store.records(request.consentRequestId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("careContextReference", r.careContextReference());
            m.put("hipId", r.hipId());
            m.put("transactionId", r.transactionId());
            m.put("receivedAt", AbdmFlows.iso(r.receivedAt()));
            m.put("checksumMatched", r.checksumMatched());
            m.put("bundle", r.bundle());
            out.add(m);
        }
        return out;
    }
}
