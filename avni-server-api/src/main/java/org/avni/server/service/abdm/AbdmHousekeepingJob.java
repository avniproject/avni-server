package org.avni.server.service.abdm;

import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.eka.EkaHiuService;
import org.avni.server.service.abdm.hip.AbdmHipStore;
import org.avni.server.service.abdm.hip.AbdmRequestException;
import org.avni.server.service.abdm.hiu.AbdmHiuDataService;
import org.avni.server.service.abdm.hiu.AbdmHiuStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// Scheduled ABDM upkeep, on ABDM's own threads (AbdmScheduler), as each organisation's system user (AbdmJobRunner):
// linking and the parked-update retry for organisations with record sharing on; the consent expiry and record erase, and
// the traffic purge, for every organisation holding what they delete, with no login where its system user cannot act.
// The task names are the keys of "jobs" in GET /web/abdm/status.
@Component
public class AbdmHousekeepingJob {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHousekeepingJob.class);
    public static final String VISIT_LINKING = "visitLinking";
    public static final String CONSENT_EXPIRY = "consentExpiry";
    public static final String CONSENT_UPDATE_RETRY = "consentUpdateRetry";
    public static final String TRAFFIC_PURGE = "trafficPurge";
    private static final int LINK_BATCH = 50;
    // Inside the 5-minute linking cadence, so runs never pile up; what is left waits for the next run.
    private static final Duration LINK_BUDGET = Duration.ofMinutes(4);
    private static final Duration UPKEEP_BUDGET = Duration.ofMinutes(30);

    private final AbdmJobRunner runner;
    private final AbdmScheduler scheduler;
    private final AbdmSettings settings;
    private final AbdmMessageLog messageLog;
    private final AbdmInbox inbox;
    private final AbdmHiuDataService hiuData;
    private final AbdmHiuStore hiuStore;
    private final AbdmHipStore hipStore;
    private final EkaHipService ekaHip;
    private final EkaHiuService ekaHiu;
    private final Clock clock;
    private final int messageRetentionDays;

    @Value("${abdm.housekeeping.traffic-cron:0 30 2 * * *}")
    private String trafficCron;
    @Value("${abdm.housekeeping.consent-cron:0 5 * * * *}")
    private String consentCron;
    @Value("${abdm.housekeeping.link-cron:0 */5 * * * *}")
    private String linkCron;

    @Autowired
    public AbdmHousekeepingJob(AbdmJobRunner runner, AbdmScheduler scheduler, AbdmSettings settings, AbdmMessageLog messageLog,
                               AbdmInbox inbox, AbdmHiuDataService hiuData, AbdmHiuStore hiuStore, AbdmHipStore hipStore,
                               EkaHipService ekaHip, EkaHiuService ekaHiu, @Value("${abdm.message-retention-days:30}") int messageRetentionDays) {
        this(runner, scheduler, settings, messageLog, inbox, hiuData, hiuStore, hipStore, ekaHip, ekaHiu, Clock.systemUTC(), messageRetentionDays);
    }

    AbdmHousekeepingJob(AbdmJobRunner runner, AbdmScheduler scheduler, AbdmSettings settings, AbdmMessageLog messageLog,
                        AbdmInbox inbox, AbdmHiuDataService hiuData, AbdmHiuStore hiuStore, AbdmHipStore hipStore,
                        EkaHipService ekaHip, EkaHiuService ekaHiu, Clock clock, int messageRetentionDays) {
        this.runner = runner;
        this.scheduler = scheduler;
        this.settings = settings;
        this.messageLog = messageLog;
        this.inbox = inbox;
        this.hiuData = hiuData;
        this.hiuStore = hiuStore;
        this.hipStore = hipStore;
        this.ekaHip = ekaHip;
        this.ekaHiu = ekaHiu;
        this.clock = clock;
        this.messageRetentionDays = messageRetentionDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.schedule(trafficCron, this::purgeOldTraffic);
        scheduler.schedule(consentCron, this::expireConsentsAndEraseRecords);
        scheduler.schedule(linkCron, this::linkQueuedVisits);
    }

    // Traffic, and webhook events handled or given up on, older than abdm.message-retention-days, in every organisation
    // holding them: they can carry a patient's details, and the duty to delete them outlives the switch.
    public void purgeOldTraffic() {
        Duration retention = Duration.ofDays(messageRetentionDays);
        Instant cutoff = clock.instant().minus(retention);
        runner.forOrganisations(TRAFFIC_PURGE, UPKEEP_BUDGET, () -> holdingOldTraffic(cutoff), deadline -> {
            messageLog.purgeOlderThan(retention);
            inbox.purgeFinishedOlderThan(cutoff);
        }, organisationId -> {
            messageLog.purgeOlderThan(organisationId, retention);
            inbox.purgeFinishedOlderThan(organisationId, cutoff);
        });
    }

    private List<Long> holdingOldTraffic(Instant cutoff) {
        TreeSet<Long> organisationIds = new TreeSet<>(messageLog.organisationsWithTrafficBefore(cutoff));
        organisationIds.addAll(inbox.organisationsWithFinishedEventsBefore(cutoff));
        return new ArrayList<>(organisationIds);
    }

    // Erasing records when their consent ends is owed by every organisation holding consents or records, whatever its
    // switches; settling requests Eka gave no clear answer to, and the retry, ask Eka, so only where record sharing is on.
    public void expireConsentsAndEraseRecords() {
        runner.forOrganisations(CONSENT_EXPIRY, UPKEEP_BUDGET, hiuStore::organisationsHoldingConsentsOrRecords, deadline -> hiuData.expireAndErase(),
                hiuData::expireAndErase);
        runner.forEachOrganisation(CONSENT_UPDATE_RETRY, UPKEEP_BUDGET, deadline -> {
            ekaHiu.settleUnknownRequests();
            ekaHiu.retryParkedUpdates();
        });
    }

    // HIP_INTI_LINK_505: visits queued on save (AbdmVisitQueue) are linked through Eka, patient by patient, under every
    // HIP ID of the organisation; each patient once, however many of its HIP IDs it has visits waiting under. No new
    // patient is taken after the organisation's deadline. The queue settles (AbdmHipStore): a link Eka never answered is
    // queued again, a failure that may pass is tried again after a growing wait, and one that cannot is given up.
    public void linkQueuedVisits() {
        runner.forEachOrganisation(VISIT_LINKING, LINK_BUDGET, deadline -> {
            Map<String, List<String>> waiting = new LinkedHashMap<>();
            settings.find().ifPresent(orgSettings -> {
                for (String hipId : orgSettings.getHipIds()) {
                    hipStore.settleLinkQueue(hipId);
                    for (String subjectUuid : hipStore.subjectsToLink(hipId, LINK_BATCH)) {
                        waiting.computeIfAbsent(subjectUuid, s -> new ArrayList<>()).add(hipId);
                    }
                }
            });
            int left = waiting.size();
            for (Map.Entry<String, List<String>> patient : waiting.entrySet()) {
                if (!clock.instant().isBefore(deadline)) {
                    logger.info("ABDM linking: {} patient(s) left for the next run", left);
                    return;
                }
                left--;
                Instant[] lookedAt = new Instant[1];
                try {
                    lookedAt[0] = hipStore.databaseNow();
                    ekaHip.linkCareContexts(patient.getKey(), true);
                } catch (EkaHipService.NoVerifiedAbha e) {
                    // Linked once a worker verifies the ABHA with Eka (AbdmVerifiedAbhaAspect), not tried meanwhile.
                    patient.getValue().forEach(hipId -> hipStore.awaitVerifiedAbha(patient.getKey(), hipId, e.getMessage(), lookedAt[0]));
                    logger.info("ABDM: visits of subject {} wait for an ABHA verified with Eka", patient.getKey());
                } catch (AbdmRequestException e) {
                    // The patient's own record refuses it (removed, a verified ABHA with neither address nor number): no later try changes that.
                    patient.getValue().forEach(hipId -> hipStore.failQueuedForGood(patient.getKey(), hipId, e.getMessage()));
                    logger.info("ABDM: visits of subject {} given up: {}", patient.getKey(), e.getMessage());
                } catch (RuntimeException e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    patient.getValue().forEach(hipId -> hipStore.failQueued(patient.getKey(), hipId, reason));
                    logger.warn("ABDM: visits of subject {} not linked, to be tried again: {}", patient.getKey(), reason);
                }
            }
        });
    }
}
