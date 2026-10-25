package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmAsync;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaHipEvents;
import org.avni.server.service.abdm.eka.EkaHiuEvents;
import org.avni.server.service.abdm.eka.EkaWebhookVerifier;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka mode: Eka Care posts every ABDM event here, on one URL, routed on the envelope's "event" field (spec section 5).
// The raw body is kept as received, because the signature is over those exact bytes. The signature is checked per
// abdm.eka.webhook-verify: off skips it, log records the outcome and carries on, enforce answers 401 to a bad one.
// Then 200 at once, and the work goes to an AbdmAsync thread as the organisation: log the event, hand it to the HIP or
// HIU handler. Handlers come through ObjectProvider, so the server starts with either side missing.
// Eka's retry policy is undocumented, so delivery is taken as at-least-once: an event id (the top-level transaction_id)
// already seen is answered 200 and dropped. DEMO-ONLY: the ids are held in memory, the last 1000, per server.
// No Avni user: the path is under /api/v3/**, unprotected (ResourceProtectionStatus).
@RestController
public class EkaWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(EkaWebhookController.class);
    private static final int SEEN_EVENTS = 1000;

    enum Route {LINK_CARE_CONTEXT, HIP_DATA_FETCH, CONSENT_UPDATE, HIU_DATA_PUSH, NONE}

    // Event names squashed to letters and digits, without the "abha"/"abdm" prefix: the spec's names and close variants.
    private static final Set<String> LINK_CARE_CONTEXT = Set.of("linkcarecontext", "linkcarecontexts", "carecontextlink",
            "carecontextlinked", "carecontextlinkstatus", "linkcarecontextstatus");
    private static final Set<String> HIP_DATA_FETCH = Set.of("hipdatafetch", "datafetch", "hipdatarequest", "hiphealthinformationrequest");
    private static final Set<String> CONSENT_UPDATE = Set.of("consentupdate", "consentupdated", "consentstatus", "consentstatusupdate",
            "consentnotify");
    private static final Set<String> HIU_DATA_PUSH = Set.of("hiudatapush", "datapush", "hiudatatransfer", "hiuhealthinformationtransfer");

    private final AbdmConfig cfg;
    private final EkaWebhookVerifier verifier;
    private final AbdmAsync async;
    private final AbdmMessageLog messageLog;
    private final ObjectProvider<EkaHipEvents> hip;
    private final ObjectProvider<EkaHiuEvents> hiu;
    private final Map<String, Boolean> seen = new LinkedHashMap<>(64, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SEEN_EVENTS;
        }
    };

    @Autowired
    public EkaWebhookController(AbdmConfig cfg, EkaWebhookVerifier verifier, AbdmAsync async, AbdmMessageLog messageLog,
                                ObjectProvider<EkaHipEvents> hip, ObjectProvider<EkaHiuEvents> hiu) {
        this.cfg = cfg;
        this.verifier = verifier;
        this.async = async;
        this.messageLog = messageLog;
        this.hip = hip;
        this.hiu = hiu;
    }

    @PostMapping(AbdmPaths.IN_EKA_WEBHOOK)
    public ResponseEntity<Map<String, String>> receive(@RequestBody(required = false) byte[] body,
                                                       @RequestHeader(value = EkaWebhookVerifier.HEADER, required = false) String signature) {
        String rawBody = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        String verify = cfg.getEkaWebhookVerify() == null ? "log" : cfg.getEkaWebhookVerify().trim().toLowerCase(Locale.ROOT);
        if (!"off".equals(verify)) {
            EkaWebhookVerifier.Result result = verifier.verify(signature, rawBody);
            if ("enforce".equals(verify) && !result.valid()) {
                logger.warn("Eka webhook refused: {}", result.reason());
                return answer(HttpStatus.UNAUTHORIZED, "rejected", result.reason());
            }
            if (result.valid()) logger.info("Eka webhook signature valid");
            else logger.warn("Eka webhook signature not valid ({}); accepted because abdm.eka.webhook-verify={}", result.reason(), verify);
        }

        JsonNode event;
        try {
            event = AbdmJson.read(rawBody);
        } catch (IllegalArgumentException e) {
            event = null;
        }
        if (event == null || !event.isObject()) {
            logger.warn("Eka webhook refused: the body is not a JSON object");
            return answer(HttpStatus.BAD_REQUEST, "rejected", "the body is not a JSON object");
        }

        String name = eventName(event);
        String eventId = AbdmFlows.firstNonNull(AbdmJson.text(event, "transaction_id"), AbdmJson.text(event, "event_id"),
                AbdmJson.text(event, "id"));
        if (eventId != null && !firstSighting(eventId)) {
            logger.info("Eka webhook {} {} already received; ignored", name, eventId);
            return answer(HttpStatus.OK, "duplicate", null);
        }
        AbdmCallbackHeaders headers = new AbdmCallbackHeaders(AbdmPaths.IN_EKA_WEBHOOK, eventId, AbdmJson.text(event, "timestamp"),
                AbdmJson.text(event, "data.hip_id"), null, null, null);
        JsonNode received = event;
        async.submit("Eka webhook " + name, () -> {
            messageLog.incoming(AbdmPaths.IN_EKA_WEBHOOK + "#" + name, headers, received);
            dispatch(route(name), name, eventId, received);
        });
        return answer(HttpStatus.OK, "accepted", null);
    }

    private void dispatch(Route route, String name, String eventId, JsonNode event) {
        switch (route) {
            case LINK_CARE_CONTEXT -> handle(hip, name, eventId, h -> h.linkCareContext(event));
            case HIP_DATA_FETCH -> handle(hip, name, eventId, h -> h.hipDataFetch(event));
            case CONSENT_UPDATE -> handle(hiu, name, eventId, h -> h.consentUpdate(event));
            case HIU_DATA_PUSH -> handle(hiu, name, eventId, h -> h.hiuDataPush(event));
            default -> logger.info("Eka webhook {} {} ignored: nothing handles this event", name, eventId);
        }
    }

    private static <T> void handle(ObjectProvider<T> handlers, String name, String eventId, Consumer<T> work) {
        T handler = handlers.getIfAvailable();
        if (handler == null) {
            logger.warn("Eka webhook {} {} has no handler; logged only", name, eventId);
            return;
        }
        work.accept(handler);
    }

    // The ABDM events carry "event"; "event_type" is what Eka's non-ABDM events use and is not read.
    static String eventName(JsonNode event) {
        String name = AbdmFlows.firstNonNull(AbdmJson.text(event, "event"), AbdmJson.text(event, "event_name"));
        return name == null ? "unknown" : name.trim();
    }

    static Route route(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        key = key.replaceFirst("^(abha|abdm)", "");
        if (LINK_CARE_CONTEXT.contains(key)) return Route.LINK_CARE_CONTEXT;
        if (HIP_DATA_FETCH.contains(key)) return Route.HIP_DATA_FETCH;
        if (CONSENT_UPDATE.contains(key)) return Route.CONSENT_UPDATE;
        if (HIU_DATA_PUSH.contains(key)) return Route.HIU_DATA_PUSH;
        return Route.NONE;
    }

    private boolean firstSighting(String eventId) {
        synchronized (seen) {
            if (seen.containsKey(eventId)) return false;
            seen.put(eventId, Boolean.TRUE);
            return true;
        }
    }

    private static ResponseEntity<Map<String, String>> answer(HttpStatus status, String outcome, String reason) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", outcome);
        if (reason != null) body.put("reason", reason);
        return ResponseEntity.status(status).body(body);
    }
}
