package org.avni.server.service.abdm.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmTime;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// DEMO-ONLY abdm-hackathon: never merged. Only in the dev and test profiles, and only in mock mode.
// An in-process stand-in for NHA's consent manager and gateway, serving the same paths, so the HIP and HIU code is the
// same in both modes. It answers our gateway calls with 202 and calls our callbacks a moment later, at
// cfg.localBaseUrl() (never the tunnel), signed "Bearer mock-gateway". It also plays the patient: approve or deny a
// consent request, and the PHR app's discover / link init / link confirm, which wait for our HIP's answer.
// All state is in memory and lost on restart.
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "abdm.gateway.mode", havingValue = "mock", matchIfMissing = true)
public class MockConsentManager {
    private static final Logger logger = LoggerFactory.getLogger(MockConsentManager.class);
    private static final String MOCK_AUTHORIZATION = "Bearer mock-gateway";
    private static final Duration WAIT_FOR_HIP = Duration.ofSeconds(10);
    private static final Duration CALLBACK_TIMEOUT = Duration.ofSeconds(10);
    private static final int RECEIVED_KEPT = 100;

    public record Answer(int status, JsonNode body) {
    }

    private static class CareContexts {
        String display;
        String hiType;
        final Map<String, String> careContexts = new LinkedHashMap<>();
    }

    private static class ConsentRequest {
        String id;
        String abhaAddress;
        String hiuId;
        JsonNode hiTypes;
        JsonNode permission;
        JsonNode purpose;
        JsonNode requester;
        String status = "REQUESTED";
        final List<String> artefactIds = new ArrayList<>();
        String createdAt = AbdmTime.now();
    }

    private final AbdmConfig cfg;
    private final Duration waitForHip;
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;

    // abhaAddress -> hipId -> patientReference -> care contexts. Guarded by this.
    private final Map<String, Map<String, Map<String, CareContexts>>> links = new LinkedHashMap<>();
    private final Map<String, ObjectNode> demographics = new LinkedHashMap<>();
    private final Set<String> linkTokens = new HashSet<>();
    private final Map<String, ConsentRequest> consentRequests = new LinkedHashMap<>();
    // consentId -> consentDetail
    private final Map<String, ObjectNode> artefacts = new LinkedHashMap<>();
    private final Map<String, ObjectNode> transactions = new LinkedHashMap<>();
    // PHR discovery transactionId -> {abhaAddress, hipId, answer}
    private final Map<String, ObjectNode> discoveries = new LinkedHashMap<>();
    // link reference -> {transactionId, abhaAddress, hipId, patientReference, careContextReferences}
    private final Map<String, ObjectNode> pendingLinks = new LinkedHashMap<>();
    private final Deque<ObjectNode> received = new ArrayDeque<>();
    private String bridgeUrl;
    // REQUEST-ID of a call we made to our HIP -> the HIP's on-* answer
    private final Map<String, CompletableFuture<JsonNode>> awaiting = new ConcurrentHashMap<>();

    @Autowired
    public MockConsentManager(AbdmConfig cfg) {
        this(cfg, WAIT_FOR_HIP);
    }

    MockConsentManager(AbdmConfig cfg, Duration waitForHip) {
        this.cfg = cfg;
        this.waitForHip = waitForHip;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
        AtomicInteger count = new AtomicInteger();
        this.scheduler = Executors.newScheduledThreadPool(2, work -> {
            Thread thread = new Thread(work, "abdm-mock-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // ---------------------------------------------------------------- the gateway, as our HIP and HIU call it

    public Answer handle(String method, String gatewayPath, HttpHeaders headers, JsonNode body) {
        HttpHeaders h = headers == null ? new HttpHeaders() : headers;
        JsonNode b = body == null || body.isNull() ? AbdmJson.obj() : body;
        String requestId = h.getFirst(AbdmPaths.H_REQUEST_ID);
        String verb = method == null ? "POST" : method.toUpperCase(Locale.ROOT);
        remember(verb, gatewayPath, requestId, h);

        if (gatewayPath.equals(cfg.getSessionPath()) && verb.equals("POST")) return session();
        if (gatewayPath.equals(cfg.getCertsPath()) && verb.equals("GET")) return new Answer(200, AbdmJson.obj().set("keys", AbdmJson.arr()));
        String authorization = h.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return error(401, "ABDM-1017", "Unauthorized: no session token");
        }

        switch (verb + " " + gatewayPath) {
            case "PATCH " + AbdmPaths.OUT_BRIDGE_URL:
                synchronized (this) {
                    bridgeUrl = AbdmJson.text(b, "url");
                }
                return accepted();
            case "GET " + AbdmPaths.OUT_BRIDGE_SERVICES:
                return new Answer(200, bridgeServices());
            case "POST " + AbdmPaths.OUT_GENERATE_TOKEN:
                return generateToken(requestId, h, b);
            case "POST " + AbdmPaths.OUT_LINK_CARE_CONTEXT:
                return linkCareContext(requestId, h, b);
            case "POST " + AbdmPaths.OUT_CONSENT_INIT:
                return consentInit(requestId, h, b);
            case "POST " + AbdmPaths.OUT_CONSENT_FETCH:
                return consentFetch(requestId, h, b);
            case "POST " + AbdmPaths.OUT_CONSENT_STATUS:
                return consentStatus(requestId, h, b);
            case "POST " + AbdmPaths.OUT_HI_REQUEST:
                return healthInformationRequest(requestId, h, b);
            case "POST " + AbdmPaths.OUT_HI_NOTIFY:
                return healthInformationNotify(b);
            case "POST " + AbdmPaths.OUT_ON_DISCOVER:
            case "POST " + AbdmPaths.OUT_LINK_ON_INIT:
            case "POST " + AbdmPaths.OUT_LINK_ON_CONFIRM:
                answerArrived(b);
                return accepted();
            case "POST " + AbdmPaths.OUT_HIP_CONSENT_ON_NOTIFY:
            case "POST " + AbdmPaths.OUT_HIU_CONSENT_ON_NOTIFY:
            case "POST " + AbdmPaths.OUT_HIP_HI_ON_REQUEST:
                return accepted();
            default:
                return error(404, "MOCK-404", "The mock gateway has no " + verb + " " + gatewayPath);
        }
    }

    private Answer session() {
        ObjectNode token = AbdmJson.obj()
                .put("accessToken", "mock-" + UUID.randomUUID())
                .put("expiresIn", 1200)
                .put("tokenType", "bearer");
        return new Answer(200, token);
    }

    private JsonNode bridgeServices() {
        ArrayNode services = AbdmJson.arr();
        ObjectNode hip = services.addObject().put("id", cfg.getHipId()).put("name", cfg.getHipName());
        hip.putArray("types").add("HIP");
        hip.put("active", true);
        ObjectNode hiu = services.addObject().put("id", cfg.getHiuId()).put("name", cfg.getHiuId());
        hiu.putArray("types").add("HIU");
        hiu.put("active", true);
        return services;
    }

    private Answer generateToken(String requestId, HttpHeaders h, JsonNode b) {
        String abhaAddress = AbdmJson.text(b, "abhaAddress");
        String linkToken = "mock-link-" + UUID.randomUUID();
        synchronized (this) {
            linkTokens.add(linkToken);
            if (abhaAddress != null) {
                ObjectNode who = AbdmJson.obj();
                for (String field : List.of("abhaNumber", "name", "gender", "yearOfBirth")) {
                    if (b.has(field)) who.set(field, b.get(field));
                }
                demographics.put(abhaAddress, who);
            }
        }
        ObjectNode callback = AbdmJson.obj();
        callback.put("abhaAddress", abhaAddress);
        callback.put("linkToken", linkToken);
        callback.set("response", answering(requestId));
        later(1, AbdmPaths.IN_HIP_ON_GENERATE_TOKEN, callback, AbdmPaths.H_HIP_ID, hipOf(h));
        return accepted();
    }

    private Answer linkCareContext(String requestId, HttpHeaders h, JsonNode b) {
        String linkToken = h.getFirst(AbdmPaths.H_LINK_TOKEN);
        if (!StringUtils.hasText(linkToken)) return error(400, "ABDM-1035", "X-LINK-TOKEN is required");
        String abhaAddress = AbdmJson.text(b, "abhaAddress");
        if (abhaAddress == null) return error(400, "MOCK-400", "abhaAddress is required");
        String hipId = hipOf(h);
        synchronized (this) {
            if (!linkTokens.contains(linkToken)) logger.warn("Mock gateway: a link token it did not issue; accepted (mock state is lost on restart)");
            recordLinks(abhaAddress, hipId, b.get("patient"));
        }
        ObjectNode callback = AbdmJson.obj();
        callback.put("abhaAddress", abhaAddress);
        callback.put("status", "SUCCESS");
        callback.set("response", answering(requestId));
        later(1, AbdmPaths.IN_HIP_ON_LINK_CARE_CONTEXT, callback, AbdmPaths.H_HIP_ID, hipId);
        return accepted();
    }

    private Answer consentInit(String requestId, HttpHeaders h, JsonNode b) {
        JsonNode consent = b.get("consent");
        String abhaAddress = AbdmJson.text(consent, "patient.id");
        if (abhaAddress == null) return error(400, "MOCK-400", "consent.patient.id is required");
        ConsentRequest request = new ConsentRequest();
        request.id = UUID.randomUUID().toString();
        request.abhaAddress = abhaAddress;
        String hiuId = AbdmJson.text(consent, "hiu.id");
        request.hiuId = hiuId != null ? hiuId : hiuOf(h);
        request.hiTypes = consent.get("hiTypes");
        request.permission = consent.get("permission");
        request.purpose = consent.get("purpose");
        request.requester = consent.get("requester");
        synchronized (this) {
            consentRequests.put(request.id, request);
        }
        ObjectNode callback = AbdmJson.obj();
        callback.putObject("consentRequest").put("id", request.id);
        callback.set("response", answering(requestId));
        later(1, AbdmPaths.IN_HIU_CONSENT_ON_INIT, callback, AbdmPaths.H_HIU_ID, request.hiuId);
        return accepted();
    }

    private Answer consentFetch(String requestId, HttpHeaders h, JsonNode b) {
        String consentId = AbdmJson.text(b, "consentId");
        ObjectNode detail;
        synchronized (this) {
            detail = consentId == null ? null : artefacts.get(consentId);
        }
        ObjectNode callback = AbdmJson.obj();
        if (detail == null) {
            callback.set("error", errorBody("ABDM-1009", "Consent artefact not found"));
        } else {
            ObjectNode consent = callback.putObject("consent");
            consent.put("status", "GRANTED");
            consent.set("consentDetail", detail.deepCopy());
            consent.put("signature", "mock");
        }
        callback.set("response", answering(requestId));
        later(1, AbdmPaths.IN_HIU_CONSENT_ON_FETCH, callback, AbdmPaths.H_HIU_ID, hiuOf(h));
        return accepted();
    }

    private Answer consentStatus(String requestId, HttpHeaders h, JsonNode b) {
        String consentRequestId = AbdmJson.text(b, "consentRequestId");
        ObjectNode callback = AbdmJson.obj();
        synchronized (this) {
            ConsentRequest request = consentRequestId == null ? null : consentRequests.get(consentRequestId);
            if (request == null) {
                callback.set("error", errorBody("ABDM-1009", "Consent request not found"));
            } else {
                ObjectNode consentRequest = callback.putObject("consentRequest");
                consentRequest.put("id", request.id);
                consentRequest.put("status", request.status);
                ArrayNode list = consentRequest.putArray("consentArtefacts");
                request.artefactIds.forEach(id -> list.addObject().put("id", id));
            }
        }
        callback.set("response", answering(requestId));
        later(1, AbdmPaths.IN_HIU_CONSENT_ON_STATUS, callback, AbdmPaths.H_HIU_ID, hiuOf(h));
        return accepted();
    }

    private Answer healthInformationRequest(String requestId, HttpHeaders h, JsonNode b) {
        JsonNode hiRequest = b.get("hiRequest");
        String consentId = AbdmJson.text(hiRequest, "consent.id");
        ObjectNode detail;
        synchronized (this) {
            detail = consentId == null ? null : artefacts.get(consentId);
        }
        ObjectNode onRequest = AbdmJson.obj();
        onRequest.set("response", answering(requestId));
        if (detail == null) {
            onRequest.set("error", errorBody("ABDM-1009", "Consent artefact not found"));
            later(1, AbdmPaths.IN_HIU_HEALTH_INFORMATION_ON_REQUEST, onRequest, AbdmPaths.H_HIU_ID, hiuOf(h));
            return accepted();
        }
        String transactionId = UUID.randomUUID().toString();
        String hipId = AbdmJson.text(detail, "hip.id");
        ObjectNode transaction = AbdmJson.obj()
                .put("transactionId", transactionId)
                .put("consentId", consentId)
                .put("hipId", hipId)
                .put("dataPushUrl", AbdmJson.text(hiRequest, "dataPushUrl"))
                .put("status", "REQUESTED");
        transaction.set("keyMaterial", hiRequest.get("keyMaterial"));
        synchronized (this) {
            transactions.put(transactionId, transaction);
        }
        onRequest.putObject("hiRequest").put("transactionId", transactionId).put("sessionStatus", "REQUESTED");
        later(1, AbdmPaths.IN_HIU_HEALTH_INFORMATION_ON_REQUEST, onRequest, AbdmPaths.H_HIU_ID, hiuOf(h));

        ObjectNode toHip = AbdmJson.obj();
        toHip.put("transactionId", transactionId);
        ObjectNode forward = toHip.putObject("hiRequest");
        forward.putObject("consent").put("id", consentId);
        forward.set("dateRange", hiRequest.get("dateRange"));
        forward.set("dataPushUrl", hiRequest.get("dataPushUrl"));
        forward.set("keyMaterial", hiRequest.get("keyMaterial"));
        later(2, AbdmPaths.IN_HIP_HEALTH_INFORMATION_REQUEST, toHip, AbdmPaths.H_HIP_ID, hipId);
        return accepted();
    }

    private Answer healthInformationNotify(JsonNode b) {
        String transactionId = AbdmJson.text(b, "notification.transactionId");
        String sessionStatus = AbdmJson.text(b, "notification.statusNotification.sessionStatus");
        synchronized (this) {
            ObjectNode transaction = transactionId == null ? null : transactions.get(transactionId);
            if (transaction != null && sessionStatus != null) transaction.put("status", sessionStatus);
        }
        return accepted();
    }

    private void answerArrived(JsonNode b) {
        String answers = AbdmJson.requestIdOf(b);
        CompletableFuture<JsonNode> waiting = answers == null ? null : awaiting.remove(answers);
        if (waiting != null) waiting.complete(b);
    }

    // ---------------------------------------------------------------- the patient, approving in the PHR app

    public JsonNode approve(String consentRequestId) {
        return decide(consentRequestId, true);
    }

    public JsonNode deny(String consentRequestId) {
        return decide(consentRequestId, false);
    }

    private JsonNode decide(String consentRequestId, boolean grant) {
        List<ObjectNode> granted = new ArrayList<>();
        ConsentRequest request;
        synchronized (this) {
            request = consentRequestId == null ? null : consentRequests.get(consentRequestId);
            if (request == null) throw new IllegalArgumentException("No consent request " + consentRequestId);
            if (!grant) {
                request.status = "DENIED";
            } else if ("GRANTED".equals(request.status)) {
                request.artefactIds.forEach(id -> granted.add(artefacts.get(id)));
            } else {
                request.status = "GRANTED";
                Map<String, Map<String, CareContexts>> byHip = links.getOrDefault(request.abhaAddress, Map.of());
                if (byHip.isEmpty()) {
                    logger.warn("Mock consent manager: consent request {} granted with no linked care contexts", request.id);
                }
                for (Map.Entry<String, Map<String, CareContexts>> hip : byHip.entrySet()) {
                    ObjectNode detail = consentDetail(request, hip.getKey(), hip.getValue());
                    String consentId = AbdmJson.text(detail, "consentId");
                    artefacts.put(consentId, detail);
                    request.artefactIds.add(consentId);
                    granted.add(detail);
                }
            }
        }
        for (ObjectNode detail : granted) {
            ObjectNode notify = AbdmJson.obj();
            ObjectNode notification = notify.putObject("notification");
            notification.put("status", "GRANTED");
            notification.put("consentId", AbdmJson.text(detail, "consentId"));
            notification.set("consentDetail", detail.deepCopy());
            notification.put("signature", "mock");
            notification.put("grantAcknowledgement", false);
            later(1, AbdmPaths.IN_HIP_CONSENT_NOTIFY, notify, AbdmPaths.H_HIP_ID, AbdmJson.text(detail, "hip.id"));
        }
        ObjectNode hiuNotify = AbdmJson.obj();
        ObjectNode notification = hiuNotify.putObject("notification");
        notification.put("consentRequestId", request.id);
        notification.put("status", request.status);
        ArrayNode ids = notification.putArray("consentArtefacts");
        granted.forEach(detail -> ids.addObject().put("id", AbdmJson.text(detail, "consentId")));
        later(1, AbdmPaths.IN_HIU_CONSENT_NOTIFY, hiuNotify, AbdmPaths.H_HIU_ID, request.hiuId);

        ObjectNode result = AbdmJson.obj();
        result.put("consentRequestId", request.id);
        result.put("status", request.status);
        result.set("consentArtefacts", ids.deepCopy());
        return result;
    }

    private ObjectNode consentDetail(ConsentRequest request, String hipId, Map<String, CareContexts> patients) {
        ObjectNode detail = AbdmJson.obj();
        detail.put("schemaVersion", "v3");
        detail.put("consentId", UUID.randomUUID().toString());
        detail.put("createdAt", AbdmTime.now());
        detail.set("purpose", request.purpose);
        detail.putObject("patient").put("id", request.abhaAddress);
        detail.putObject("consentManager").put("id", cfg.getCmId());
        ObjectNode hip = detail.putObject("hip").put("id", hipId);
        if (hipId.equals(cfg.getHipId())) hip.put("name", cfg.getHipName());
        detail.putObject("hiu").put("id", request.hiuId);
        detail.set("requester", request.requester);
        ArrayNode careContexts = detail.putArray("careContexts");
        patients.forEach((patientReference, contexts) -> contexts.careContexts.keySet().forEach(reference ->
                careContexts.addObject().put("patientReference", patientReference).put("careContextReference", reference)));
        detail.set("hiTypes", request.hiTypes);
        detail.set("permission", request.permission);
        return detail;
    }

    // ---------------------------------------------------------------- the patient, linking from the PHR app

    public JsonNode phrDiscover(String abhaAddress, String hipId) {
        return phrDiscover(abhaAddress, hipId, null);
    }

    // who: optional {name, gender, yearOfBirth} to send in place of what generate-token told the mock.
    public JsonNode phrDiscover(String abhaAddress, String hipId, JsonNode who) {
        if (!StringUtils.hasText(abhaAddress)) throw new IllegalArgumentException("abhaAddress is required");
        String hip = StringUtils.hasText(hipId) ? hipId : cfg.getHipId();
        String transactionId = UUID.randomUUID().toString();
        ObjectNode known;
        synchronized (this) {
            known = demographics.getOrDefault(abhaAddress, AbdmJson.obj()).deepCopy();
        }
        if (who != null && who.isObject()) who.fields().forEachRemaining(field -> known.set(field.getKey(), field.getValue()));
        ObjectNode body = AbdmJson.obj();
        body.put("transactionId", transactionId);
        ObjectNode patient = body.putObject("patient");
        patient.put("id", abhaAddress);
        ArrayNode verified = patient.putArray("verifiedIdentifiers");
        verified.addObject().put("type", "ABHA_ADDRESS").put("value", abhaAddress);
        if (AbdmJson.text(known, "abhaNumber") != null) {
            verified.addObject().put("type", "ABHA_NUMBER").put("value", AbdmJson.text(known, "abhaNumber"));
        }
        patient.putArray("unverifiedIdentifiers");
        for (String field : List.of("name", "gender", "yearOfBirth")) {
            if (known.has(field)) patient.set(field, known.get(field));
        }
        JsonNode answer = callAndWait(AbdmPaths.IN_HIP_DISCOVER, body, hip);
        ObjectNode discovery = AbdmJson.obj().put("abhaAddress", abhaAddress).put("hipId", hip);
        discovery.set("answer", answer);
        synchronized (this) {
            discoveries.put(transactionId, discovery);
        }
        return answer;
    }

    // Returns the HIP's link reference number.
    public String phrLinkInit(String transactionId, String abhaAddress, String patientReference, List<String> careContextRefs) {
        if (!StringUtils.hasText(patientReference)) throw new IllegalArgumentException("patientReference is required");
        if (careContextRefs == null || careContextRefs.isEmpty()) throw new IllegalArgumentException("careContextReferences are required");
        ObjectNode discovery;
        synchronized (this) {
            discovery = transactionId == null ? null : discoveries.get(transactionId);
        }
        String hip = discovery != null ? AbdmJson.text(discovery, "hipId") : cfg.getHipId();
        String address = StringUtils.hasText(abhaAddress) ? abhaAddress : AbdmJson.text(discovery, "abhaAddress");
        JsonNode discovered = discovery == null ? null : patientIn(discovery.get("answer"), patientReference);
        String hiType = discovered == null ? null : AbdmJson.text(discovered, "hiType");

        ObjectNode body = AbdmJson.obj();
        body.put("transactionId", transactionId == null ? UUID.randomUUID().toString() : transactionId);
        body.put("abhaAddress", address);
        ObjectNode patient = body.putArray("patient").addObject();
        patient.put("referenceNumber", patientReference);
        ArrayNode contexts = patient.putArray("careContexts");
        careContextRefs.forEach(reference -> contexts.addObject().put("referenceNumber", reference));
        patient.put("hiType", hiType == null ? "OPConsultation" : hiType);
        patient.put("count", careContextRefs.size());

        JsonNode answer = callAndWait(AbdmPaths.IN_HIP_LINK_INIT, body, hip);
        String linkReference = AbdmJson.text(answer, "link.referenceNumber");
        if (linkReference == null) {
            throw new AbdmGatewayException(0, AbdmJson.text(answer, "error.code"),
                    "The HIP answered link init without a link reference" + reason(answer));
        }
        ObjectNode pending = AbdmJson.obj()
                .put("transactionId", AbdmJson.text(body, "transactionId"))
                .put("abhaAddress", address)
                .put("hipId", hip)
                .put("patientReference", patientReference);
        pending.set("careContextReferences", AbdmJson.M.valueToTree(careContextRefs));
        synchronized (this) {
            pendingLinks.put(linkReference, pending);
        }
        return linkReference;
    }

    public JsonNode phrLinkConfirm(String linkRefNumber, String otp) {
        if (!StringUtils.hasText(linkRefNumber)) throw new IllegalArgumentException("linkReference is required");
        ObjectNode pending;
        synchronized (this) {
            pending = pendingLinks.get(linkRefNumber);
        }
        String hip = pending != null ? AbdmJson.text(pending, "hipId") : cfg.getHipId();
        ObjectNode body = AbdmJson.obj();
        body.putObject("confirmation").put("linkRefNumber", linkRefNumber).put("token", StringUtils.hasText(otp) ? otp : cfg.getDemoOtp());

        JsonNode answer = callAndWait(AbdmPaths.IN_HIP_LINK_CONFIRM, body, hip);
        ObjectNode result = AbdmJson.obj();
        JsonNode patients = answer.get("patient");
        if (answer.has("error") || patients == null || patients.isNull() || patients.isMissingNode()) {
            result.put("status", "FAILED");
            result.set("error", answer.get("error"));
            return result;
        }
        String abhaAddress = pending == null ? null : AbdmJson.text(pending, "abhaAddress");
        if (abhaAddress != null) {
            synchronized (this) {
                recordLinks(abhaAddress, hip, patients);
                pendingLinks.remove(linkRefNumber);
            }
        } else {
            logger.warn("Mock consent manager: link reference it did not start; links not recorded");
        }
        result.put("status", "LINKED");
        result.set("patient", patients);
        return result;
    }

    private JsonNode callAndWait(String inPath, ObjectNode body, String hipId) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> answer = new CompletableFuture<>();
        awaiting.put(requestId, answer);
        try {
            int status = send(inPath, body, AbdmPaths.H_HIP_ID, hipId, requestId);
            if (status < 200 || status > 299) {
                throw new AbdmGatewayException(status, null, "The HIP refused " + inPath + " with HTTP " + status);
            }
            return answer.get(waitForHip.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new AbdmGatewayException(0, null, "The HIP did not answer " + inPath + " within " + waitForHip.toMillis() + " ms");
        } catch (ExecutionException e) {
            throw new AbdmGatewayException(0, null, "The HIP's answer to " + inPath + " failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AbdmGatewayException(0, null, "Waiting for the HIP's answer to " + inPath + " was interrupted");
        } finally {
            awaiting.remove(requestId);
        }
    }

    // ---------------------------------------------------------------- state

    public synchronized JsonNode state() {
        ObjectNode state = AbdmJson.obj();
        state.put("bridgeUrl", bridgeUrl);
        ObjectNode linkState = state.putObject("links");
        links.forEach((abhaAddress, byHip) -> {
            ObjectNode hips = linkState.putObject(abhaAddress);
            byHip.forEach((hipId, patients) -> {
                ArrayNode list = hips.putArray(hipId);
                patients.forEach((patientReference, contexts) -> {
                    ObjectNode patient = list.addObject().put("referenceNumber", patientReference).put("display", contexts.display).put("hiType", contexts.hiType);
                    ArrayNode careContexts = patient.putArray("careContexts");
                    contexts.careContexts.forEach((reference, display) -> careContexts.addObject().put("referenceNumber", reference).put("display", display));
                });
            });
        });
        ArrayNode requests = state.putArray("consentRequests");
        consentRequests.values().forEach(request -> {
            ObjectNode row = requests.addObject()
                    .put("id", request.id)
                    .put("abhaAddress", request.abhaAddress)
                    .put("hiuId", request.hiuId)
                    .put("status", request.status)
                    .put("createdAt", request.createdAt);
            row.set("purpose", request.purpose);
            row.set("hiTypes", request.hiTypes);
            row.set("permission", request.permission);
            row.set("artefactIds", AbdmJson.M.valueToTree(request.artefactIds));
        });
        ArrayNode artefactList = state.putArray("artefacts");
        artefacts.values().forEach(detail -> artefactList.add(detail.deepCopy()));
        ArrayNode transactionList = state.putArray("transactions");
        transactions.values().forEach(transaction -> transactionList.add(transaction.deepCopy()));
        ArrayNode pending = state.putArray("pendingLinks");
        pendingLinks.forEach((reference, link) -> pending.add(link.deepCopy().put("linkReference", reference)));
        ArrayNode calls = state.putArray("received");
        received.forEach(call -> calls.add(call.deepCopy()));
        return state;
    }

    // ---------------------------------------------------------------- helpers

    private synchronized void remember(String method, String path, String requestId, HttpHeaders h) {
        ObjectNode call = AbdmJson.obj()
                .put("at", AbdmTime.now())
                .put("method", method)
                .put("path", path)
                .put("requestId", requestId)
                .put("hipId", h.getFirst(AbdmPaths.H_HIP_ID))
                .put("hiuId", h.getFirst(AbdmPaths.H_HIU_ID));
        received.addFirst(call);
        while (received.size() > RECEIVED_KEPT) received.removeLast();
    }

    // Caller holds the lock. patients: an array (or one object) of {referenceNumber, display, careContexts, hiType}.
    private void recordLinks(String abhaAddress, String hipId, JsonNode patients) {
        if (patients == null || patients.isNull() || patients.isMissingNode()) return;
        List<JsonNode> list = new ArrayList<>();
        if (patients.isArray()) patients.forEach(list::add);
        else list.add(patients);
        Map<String, CareContexts> byPatient = links.computeIfAbsent(abhaAddress, a -> new LinkedHashMap<>())
                .computeIfAbsent(hipId, hip -> new LinkedHashMap<>());
        for (JsonNode patient : list) {
            String patientReference = AbdmJson.text(patient, "referenceNumber");
            if (patientReference == null) continue;
            CareContexts contexts = byPatient.computeIfAbsent(patientReference, p -> new CareContexts());
            if (AbdmJson.text(patient, "display") != null) contexts.display = AbdmJson.text(patient, "display");
            if (AbdmJson.text(patient, "hiType") != null) contexts.hiType = AbdmJson.text(patient, "hiType");
            JsonNode careContexts = patient.get("careContexts");
            if (careContexts != null && careContexts.isArray()) {
                for (JsonNode careContext : careContexts) {
                    String reference = AbdmJson.text(careContext, "referenceNumber");
                    if (reference != null) contexts.careContexts.put(reference, AbdmJson.text(careContext, "display"));
                }
            }
        }
    }

    private static JsonNode patientIn(JsonNode discoverAnswer, String patientReference) {
        JsonNode patients = discoverAnswer == null ? null : discoverAnswer.get("patient");
        if (patients == null) return null;
        if (!patients.isArray()) return patientReference.equals(AbdmJson.text(patients, "referenceNumber")) ? patients : null;
        for (JsonNode patient : patients) {
            if (patientReference.equals(AbdmJson.text(patient, "referenceNumber"))) return patient;
        }
        return null;
    }

    private static String reason(JsonNode answer) {
        String message = AbdmJson.text(answer, "error.message");
        return message == null ? "" : ": " + message;
    }

    private String hipOf(HttpHeaders h) {
        String hip = h.getFirst(AbdmPaths.H_HIP_ID);
        return StringUtils.hasText(hip) ? hip : cfg.getHipId();
    }

    private String hiuOf(HttpHeaders h) {
        String hiu = h.getFirst(AbdmPaths.H_HIU_ID);
        return StringUtils.hasText(hiu) ? hiu : cfg.getHiuId();
    }

    private static ObjectNode answering(String requestId) {
        return AbdmJson.obj().put("requestId", requestId);
    }

    private static ObjectNode errorBody(String code, String message) {
        return AbdmJson.obj().put("code", code).put("message", message);
    }

    private static Answer accepted() {
        return new Answer(202, null);
    }

    private static Answer error(int status, String code, String message) {
        ObjectNode body = AbdmJson.obj();
        body.set("error", errorBody(code, message));
        return new Answer(status, body);
    }

    // Calls our callback after `steps` callback delays, on the mock's own threads.
    private void later(int steps, String inPath, ObjectNode body, String idHeader, String idValue) {
        long delay = Math.max(0, cfg.getMockCallbackDelayMs()) * steps;
        try {
            scheduler.schedule(() -> {
                try {
                    send(inPath, body, idHeader, idValue, UUID.randomUUID().toString());
                } catch (RuntimeException e) {
                    logger.warn("Mock gateway callback {} failed: {}", inPath, e.getMessage());
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            logger.warn("Mock gateway callback {} not scheduled: shut down", inPath);
        }
    }

    private int send(String inPath, JsonNode body, String idHeader, String idValue, String requestId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(cfg.localBaseUrl() + inPath))
                .header(AbdmPaths.H_REQUEST_ID, requestId)
                .header(AbdmPaths.H_TIMESTAMP, AbdmTime.now())
                .header(AbdmPaths.H_CM_ID, cfg.getCmId())
                .header(HttpHeaders.AUTHORIZATION, MOCK_AUTHORIZATION)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(AbdmJson.write(body)));
        if (idValue != null) builder.header(idHeader, idValue);
        CompletableFuture<HttpResponse<Void>> response = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding());
        try {
            int status = response.get(CALLBACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).statusCode();
            logger.info("Mock gateway -> POST {} -> {} (request {})", inPath, status, requestId);
            return status;
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new AbdmGatewayException(0, null, "Our server did not answer " + inPath + " within " + CALLBACK_TIMEOUT.getSeconds() + " s");
        } catch (ExecutionException e) {
            throw new AbdmGatewayException(0, null, "Our server did not answer " + inPath + ": " + e.getCause().getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AbdmGatewayException(0, null, "Calling " + inPath + " was interrupted");
        }
    }
}
