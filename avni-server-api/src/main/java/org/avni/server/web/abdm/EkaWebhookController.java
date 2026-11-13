package org.avni.server.web.abdm;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmInbox;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.eka.EkaEventGate;
import org.avni.server.service.abdm.eka.EkaEventRoute;
import org.avni.server.service.abdm.eka.EkaEventWorker;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.eka.EkaWebhookVerifier;
import org.avni.server.service.abdm.hip.AbdmFlows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Eka Care posts each organisation's ABDM events to that organisation's own address, /api/v3/eka/webhook/{webhookId}
// (its Eka settings' webhookId). In order: the address must name an organisation (else 404); the signature over the raw
// body, as received, must check with that organisation's key (else 401, saying nothing more); the organisation must take
// the event (EkaEventGate: record sharing on, or a consent ending while it holds consents or records; else 404). Then the
// event is stored (AbdmInbox) and only then answered 200: an event that could not be stored is not acknowledged, so Eka
// delivers it again. EkaEventWorker handles it as the organisation.
// Avni's own faults (a key it cannot read, a store that failed) answer 500, so Eka delivers again, and go to Bugsnag.
// No Avni user: this path alone is open (ResourceProtectionStatus), so the signature is the check.
@RestController
public class EkaWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(EkaWebhookController.class);
    private static final String SOURCE = "eka";
    private static final int MAX_EVENT_ID = 100;
    private static final int MAX_TEXT = 255;
    // Eka's own signature outside the time allowed this often points at a clock, this server's or Eka's, not a replay.
    static final int STALE_REPEATS = 3;
    // An organisation's same alarm at most once in this time: Eka delivers again and again while the fault lasts.
    static final Duration ALARM_WINDOW = Duration.ofMinutes(15);

    private final ExternalSystemConfigRepository configRepository;
    private final EkaWebhookVerifier verifier;
    private final EkaEventGate gate;
    private final AbdmInbox inbox;
    private final EkaEventWorker worker;
    private final Bugsnag bugsnag;
    private final Map<Long, Deque<Instant>> staleRefusals = new ConcurrentHashMap<>();
    private final Map<Long, Instant> keyAlarms = new ConcurrentHashMap<>();

    @Autowired
    public EkaWebhookController(ExternalSystemConfigRepository configRepository, EkaWebhookVerifier verifier, EkaEventGate gate,
                                AbdmInbox inbox, EkaEventWorker worker, Bugsnag bugsnag) {
        this.configRepository = configRepository;
        this.verifier = verifier;
        this.gate = gate;
        this.inbox = inbox;
        this.worker = worker;
        this.bugsnag = bugsnag;
    }

    @PostMapping(AbdmPaths.IN_EKA_WEBHOOK + "/{webhookId}")
    public ResponseEntity<Map<String, String>> receive(@PathVariable String webhookId, @RequestBody(required = false) byte[] body,
                                                       @RequestHeader(value = EkaWebhookVerifier.HEADER, required = false) String signature) {
        Optional<EkaSystemConfig> settings = configRepository.findEkaSystemConfigByWebhookId(webhookId);
        if (settings.isEmpty()) {
            logger.warn("Eka webhook refused: no organisation has this webhook address");
            return answer(HttpStatus.NOT_FOUND, "rejected", null);
        }
        long organisationId = settings.get().getOrganisationId();
        String rawBody = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        EkaWebhookVerifier.Result result = verifier.verify(settings.get(), signature, rawBody);
        if (result.kind() == EkaWebhookVerifier.Kind.KEY_UNUSABLE) {
            // An organisation that takes no event (ABDM off, holding nothing a withdrawal would end; an ABHA-only one keeps
            // its webhook address) needs no key: refused as the gate would refuse it, with no alarm.
            if (!gate.takesAnyEvent(organisationId)) {
                logger.warn("Eka webhook for organisation {} refused: it takes no event, and has no signing key", organisationId);
                return answer(HttpStatus.NOT_FOUND, "rejected", null);
            }
            IllegalStateException fault = new IllegalStateException("Eka webhook for organisation " + organisationId + " not checked: " + result.reason());
            logger.error(fault.getMessage());
            if (firstInWindow(keyAlarms, organisationId)) bugsnag.notify(fault);
            return answer(HttpStatus.INTERNAL_SERVER_ERROR, "not checked", null);
        }
        if (!result.valid()) {
            logger.warn("Eka webhook for organisation {} refused: {}", organisationId, result.reason());
            if (result.kind() == EkaWebhookVerifier.Kind.STALE) staleRefused(organisationId);
            return answer(HttpStatus.UNAUTHORIZED, "rejected", null);
        }
        JsonNode event;
        try {
            event = AbdmJson.read(rawBody);
        } catch (IllegalArgumentException e) {
            event = null;
        }
        if (event == null || !event.isObject()) {
            logger.warn("Eka webhook for organisation {} refused: the body is not a JSON object", organisationId);
            return answer(HttpStatus.BAD_REQUEST, "rejected", "the body is not a JSON object");
        }

        String name = bounded(EkaEventRoute.eventName(event));
        EkaEventRoute route = EkaEventRoute.of(name);
        if (!gate.admits(organisationId, route, event)) {
            logger.warn("Eka webhook {} for organisation {} refused: its record sharing is off", name, organisationId);
            return answer(HttpStatus.NOT_FOUND, "rejected", null);
        }
        String eventId = eventId(event, rawBody);
        try {
            if (!inbox.receive(organisationId, SOURCE, eventId, name, route.name(), consentKey(organisationId, route, event), rawBody, Instant.now())) {
                logger.info("Eka webhook {} {} for organisation {} was stored before; accepted again", name, eventId, organisationId);
            }
        } catch (RuntimeException e) {
            logger.error("Eka webhook {} {} for organisation {} could not be stored, so it is not acknowledged", name, eventId, organisationId, e);
            bugsnag.notify(new IllegalStateException("Eka webhook " + name + " for organisation " + organisationId + " not stored", e));
            return answer(HttpStatus.INTERNAL_SERVER_ERROR, "not stored", null);
        }
        worker.wake();
        return answer(HttpStatus.OK, "accepted", null);
    }

    // Reported once per STALE_REPEATS refusals of an organisation within ALARM_WINDOW; one alone may be a replay.
    private void staleRefused(long organisationId) {
        Instant now = Instant.now();
        Deque<Instant> refusals = staleRefusals.computeIfAbsent(organisationId, id -> new ArrayDeque<>());
        boolean repeated;
        synchronized (refusals) {
            while (!refusals.isEmpty() && refusals.peekFirst().isBefore(now.minus(ALARM_WINDOW))) refusals.pollFirst();
            refusals.addLast(now);
            repeated = refusals.size() >= STALE_REPEATS;
            if (repeated) refusals.clear();
        }
        if (!repeated) return;
        IllegalStateException fault = new IllegalStateException("Eka webhooks for organisation " + organisationId + " refused " + STALE_REPEATS
                + " times within " + ALARM_WINDOW.toMinutes() + " minutes: signed outside the " + EkaWebhookVerifier.TOLERANCE_SECONDS
                + " s allowed; check this server's clock");
        logger.error(fault.getMessage());
        bugsnag.notify(fault);
    }

    // True for the organisation's first alarm, and again only once ALARM_WINDOW has passed since the last one.
    private static boolean firstInWindow(Map<Long, Instant> lastAlarms, long organisationId) {
        Instant now = Instant.now();
        boolean[] first = {false};
        lastAlarms.compute(organisationId, (id, last) -> {
            first[0] = last == null || !last.isAfter(now.minus(ALARM_WINDOW));
            return first[0] ? now : last;
        });
        return first[0];
    }

    // Eka's top-level transaction_id names the delivery; without one, the body itself, so a redelivery is still one event.
    private static String eventId(JsonNode event, String rawBody) {
        String id = AbdmFlows.firstNonNull(AbdmJson.text(event, "transaction_id"), AbdmJson.text(event, "event_id"), AbdmJson.text(event, "id"));
        return id != null && !id.isBlank() && id.length() <= MAX_EVENT_ID ? id : "sha256:" + sha256(rawBody);
    }

    // The consent an HIU event concerns: the inbox handles a consent's events in the order they came. An update naming
    // only artefacts (a withdrawal) takes the consent of a waiting update that names them, so it follows its grant.
    private String consentKey(long organisationId, EkaEventRoute route, JsonNode event) {
        String key = switch (route) {
            case CONSENT_UPDATE -> {
                String request = EkaHiuService.consentRequestIdOf(event);
                yield request != null ? request : inbox.pendingConsentKeyOf(organisationId, EkaHiuService.artefactIdsOf(event)).orElse(null);
            }
            case HIU_DATA_PUSH -> EkaHiuService.pushConsentIdOf(event);
            default -> null;
        };
        return bounded(key);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String bounded(String text) {
        return text == null || text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    private static ResponseEntity<Map<String, String>> answer(HttpStatus status, String outcome, String reason) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", outcome);
        if (reason != null) body.put("reason", reason);
        return ResponseEntity.status(status).body(body);
    }
}
