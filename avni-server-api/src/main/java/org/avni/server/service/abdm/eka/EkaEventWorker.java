package org.avni.server.service.abdm.eka;

import com.bugsnag.Bugsnag;
import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmInbox;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.AbdmNotReadyException;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmOrganisation;
import org.avni.server.service.abdm.AbdmPaths;
import org.avni.server.service.abdm.AbdmScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

// Handles the Eka webhook events EkaWebhookController stored (AbdmInbox), on ABDM's own threads (AbdmScheduler): at once
// when an event comes in, and every few seconds for events waiting for another try. Each event is handed to its handler as
// its organisation's system user, once the organisation is checked to still take it (EkaEventGate). A handler that
// throws leaves the event for a later try; at the inbox's cap it fails, logged with its stack trace and sent to Bugsnag.
// A consent's withdrawal, refusal or expiry is never given up: past the cap each failed try alarms as not applied, and
// where the system user cannot act it is applied with no login.
// Handlers come through ObjectProvider, so the server starts with either side missing.
@Component
public class EkaEventWorker {
    private static final Logger logger = LoggerFactory.getLogger(EkaEventWorker.class);
    // Inside the poll's cadence for a single drain, so one busy organisation cannot hold a thread for long.
    static final Duration DRAIN_BUDGET = Duration.ofMinutes(2);
    private static final int BATCH = 10;

    private final AbdmInbox inbox;
    private final EkaEventGate gate;
    private final AbdmOrgContext orgContext;
    private final AbdmMessageLog messageLog;
    private final AbdmScheduler scheduler;
    private final Bugsnag bugsnag;
    private final ObjectProvider<EkaHipEvents> hip;
    private final ObjectProvider<EkaHiuEvents> hiu;
    private final ObjectProvider<EkaHipDiscovery> discovery;
    private final Clock clock;
    private final AtomicBoolean woken = new AtomicBoolean();

    // A patient's withdrawal, refusal or expiry still not applied after the inbox's cap of tries.
    public static class WithdrawalNotApplied extends RuntimeException {
        WithdrawalNotApplied(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Value("${abdm.inbox.cron:*/10 * * * * *}")
    private String inboxCron;
    @Value("${abdm.inbox.sweep-cron:0 * * * * *}")
    private String sweepCron;

    @Autowired
    public EkaEventWorker(AbdmInbox inbox, EkaEventGate gate, AbdmOrgContext orgContext, AbdmMessageLog messageLog, AbdmScheduler scheduler,
                          Bugsnag bugsnag, ObjectProvider<EkaHipEvents> hip, ObjectProvider<EkaHiuEvents> hiu,
                          ObjectProvider<EkaHipDiscovery> discovery) {
        this(inbox, gate, orgContext, messageLog, scheduler, bugsnag, hip, hiu, discovery, Clock.systemUTC());
    }

    EkaEventWorker(AbdmInbox inbox, EkaEventGate gate, AbdmOrgContext orgContext, AbdmMessageLog messageLog, AbdmScheduler scheduler,
                   Bugsnag bugsnag, ObjectProvider<EkaHipEvents> hip, ObjectProvider<EkaHiuEvents> hiu,
                   ObjectProvider<EkaHipDiscovery> discovery, Clock clock) {
        this.inbox = inbox;
        this.gate = gate;
        this.orgContext = orgContext;
        this.messageLog = messageLog;
        this.scheduler = scheduler;
        this.bugsnag = bugsnag;
        this.hip = hip;
        this.hiu = hiu;
        this.discovery = discovery;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.schedule(inboxCron, this::drainReporting);
        scheduler.schedule(sweepCron, this::sweep);
    }

    // An event was stored: drain on an ABDM thread now rather than at the next poll. A wake already pending covers this one.
    public void wake() {
        if (!woken.compareAndSet(false, true)) return;
        scheduler.execute(() -> {
            woken.set(false);
            drainReporting();
        });
    }

    // Handles due events until none is left or the budget is spent; what is left waits for the next drain.
    public int drain() {
        Instant deadline = clock.instant().plus(DRAIN_BUDGET);
        int handled = 0;
        while (clock.instant().isBefore(deadline)) {
            List<AbdmInbox.Event> batch = inbox.claim(BATCH, clock.instant());
            if (batch.isEmpty()) break;
            for (AbdmInbox.Event event : batch) {
                handle(event);
                handled++;
            }
        }
        return handled;
    }

    public void sweep() {
        try {
            AbdmInbox.Sweep sweep = inbox.sweep(clock.instant());
            if (sweep.returned() > 0) {
                logger.warn("Eka webhook: {} event(s) left in progress by a stopped worker put back for another try", sweep.returned());
            }
            if (sweep.failed() > 0) {
                IllegalStateException e = new IllegalStateException(sweep.failed() + " Eka webhook event(s) given up: left in progress on their last try");
                logger.error(e.getMessage(), e);
                bugsnag.notify(e);
            }
        } catch (RuntimeException e) {
            logger.error("Eka webhook sweep failed", e);
            bugsnag.notify(e);
        }
    }

    private void drainReporting() {
        try {
            drain();
        } catch (RuntimeException e) {
            logger.error("Eka webhook worker failed", e);
            bugsnag.notify(e);
        }
    }

    private void handle(AbdmInbox.Event event) {
        String what = "Eka webhook " + event.eventName() + " " + event.eventId() + " of organisation " + event.organisationId();
        boolean endsConsent = false;
        try {
            JsonNode body = AbdmJson.read(event.body());
            endsConsent = routeOf(event) == EkaEventRoute.CONSENT_UPDATE && EkaHiuService.endsConsent(body);
            if (!gate.admits(event.organisationId(), routeOf(event), body)) {
                inbox.fail(event.id(), "Record sharing is off for the organisation", clock.instant());
                logger.warn("{} not handled: record sharing is off for the organisation", what);
                return;
            }
            AbdmOrganisation organisation = orgContext.organisation(event.organisationId());
            try {
                orgContext.runAs(organisation, () -> dispatch(event, body));
            } catch (AbdmOrgContext.SystemUserException e) {
                if (!endsConsent || !appliedWithoutLogin(event, body, what, e)) throw e;
            }
            inbox.done(event.id());
        } catch (AbdmNotReadyException e) {
            retry(event, what, e, false, endsConsent);
        } catch (RuntimeException e) {
            retry(event, what, e, true, endsConsent);
        }
    }

    // The system user cannot act, so a patient's withdrawal is applied with no login; reported, as the user needs fixing.
    private boolean appliedWithoutLogin(AbdmInbox.Event event, JsonNode body, String what, AbdmOrgContext.SystemUserException cause) {
        EkaHiuEvents handler = hiu.getIfAvailable();
        if (handler == null) return false;
        boolean whole = orgContext.withoutLogin(() -> handler.endConsentWithoutLogin(event.organisationId(), body));
        if (whole) {
            logger.error("{} applied with no login: {}", what, cause.getMessage(), cause);
            bugsnag.notify(cause);
        }
        return whole;
    }

    private void retry(AbdmInbox.Event event, String what, RuntimeException e, boolean unexpected, boolean endsConsent) {
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (endsConsent) {
            inbox.retryUntilHandled(event, error, clock.instant());
            if (event.attempts() >= AbdmInbox.MAX_ATTEMPTS) {
                WithdrawalNotApplied alarm = new WithdrawalNotApplied("Withdrawal not applied: " + what + " failed " + event.attempts()
                        + " tries, and is tried again every " + AbdmInbox.PAST_CAP_WAIT.toMinutes() + " minutes: " + error, e);
                logger.error(alarm.getMessage(), e);
                bugsnag.notify(alarm);
            } else {
                logger.warn("{} failed on try {}; it will be tried again until it is applied", what, event.attempts(), e);
            }
            return;
        }
        boolean failed = inbox.retry(event, error, clock.instant());
        if (failed) {
            logger.error("{} given up after {} tries", what, event.attempts(), e);
            bugsnag.notify(e);
        } else if (unexpected) {
            logger.warn("{} failed on try {}; it will be tried again", what, event.attempts(), e);
        } else {
            logger.info("{} waits for another try: {}", what, e.getMessage());
        }
    }

    private void dispatch(AbdmInbox.Event event, JsonNode body) {
        String name = event.eventName();
        // Traffic is logged on the first try only, so a retried event is one entry.
        if (event.attempts() == 1) {
            AbdmCallbackHeaders headers = new AbdmCallbackHeaders(AbdmPaths.IN_EKA_WEBHOOK, event.eventId(), AbdmJson.text(body, "timestamp"),
                    AbdmJson.text(body, "data.hip_id"), null, null, null);
            messageLog.incoming(AbdmPaths.IN_EKA_WEBHOOK + "#" + name, headers, body);
        }
        // A share's handler throws a failure that may pass, for another try, until the inbox's last try.
        boolean lastTry = event.attempts() >= AbdmInbox.MAX_ATTEMPTS;
        switch (routeOf(event)) {
            case LINK_CARE_CONTEXT -> handOver(hip, event, h -> h.linkCareContext(body));
            case HIP_DATA_FETCH -> handOver(hip, event, h -> h.hipDataFetch(body, lastTry));
            case CONSENT_UPDATE -> handOver(hiu, event, h -> h.consentUpdate(body));
            case HIU_DATA_PUSH -> handOver(hiu, event, h -> h.hiuDataPush(body, lastTry));
            case DISCOVER -> handOver(discovery, event, d -> d.discover(body));
            case DISCOVER_LINK_INIT -> handOver(discovery, event, d -> d.linkInit(body));
            case DISCOVER_LINK_CONFIRM -> handOver(discovery, event, d -> d.linkConfirm(body));
            default -> logger.info("Eka webhook {} {} ignored: nothing handles this event", name, event.eventId());
        }
    }

    private static EkaEventRoute routeOf(AbdmInbox.Event event) {
        try {
            return event.route() == null ? EkaEventRoute.of(event.eventName()) : EkaEventRoute.valueOf(event.route());
        } catch (IllegalArgumentException e) {
            return EkaEventRoute.of(event.eventName());
        }
    }

    private static <T> void handOver(ObjectProvider<T> handlers, AbdmInbox.Event event, Consumer<T> work) {
        T handler = handlers.getIfAvailable();
        if (handler == null) {
            logger.warn("Eka webhook {} {} has no handler; logged only", event.eventName(), event.eventId());
            return;
        }
        work.accept(handler);
    }
}
