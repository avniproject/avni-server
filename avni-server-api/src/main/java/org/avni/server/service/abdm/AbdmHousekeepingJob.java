package org.avni.server.service.abdm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// DEMO-ONLY abdm-hackathon: never merged.
// Scheduled ABDM upkeep, as the bridge's one organisation (AbdmOrgContext). Without abdm.system-user nothing runs.
@Component
public class AbdmHousekeepingJob {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHousekeepingJob.class);
    private static final int LINK_BATCH = 50;

    private final AbdmConfig cfg;
    private final AbdmSettings settings;
    private final AbdmOrgContext orgContext;
    private final AbdmMessageLog messageLog;
    private final org.avni.server.service.abdm.hiu.AbdmHiuDataService hiuData;
    private final org.avni.server.service.abdm.hip.AbdmHipStore hipStore;
    private final org.avni.server.service.abdm.eka.EkaHipService ekaHip;
    private final org.avni.server.service.abdm.eka.EkaHiuService ekaHiu;
    private final int messageRetentionDays;

    @Autowired
    public AbdmHousekeepingJob(AbdmConfig cfg, AbdmSettings settings, AbdmOrgContext orgContext, AbdmMessageLog messageLog,
                               org.avni.server.service.abdm.hiu.AbdmHiuDataService hiuData,
                               org.avni.server.service.abdm.hip.AbdmHipStore hipStore,
                               org.avni.server.service.abdm.eka.EkaHipService ekaHip,
                               org.avni.server.service.abdm.eka.EkaHiuService ekaHiu,
                               @Value("${abdm.message-retention-days:30}") int messageRetentionDays) {
        this.hipStore = hipStore;
        this.ekaHip = ekaHip;
        this.ekaHiu = ekaHiu;
        this.hiuData = hiuData;
        this.cfg = cfg;
        this.settings = settings;
        this.orgContext = orgContext;
        this.messageLog = messageLog;
        this.messageRetentionDays = messageRetentionDays;
    }

    @Scheduled(cron = "${abdm.housekeeping.traffic-cron:0 30 2 * * *}", zone = "Asia/Kolkata")
    public void purgeOldTraffic() {
        runAsOrg("traffic purge", () -> messageLog.purgeOlderThan(Duration.ofDays(messageRetentionDays)));
    }

    @Scheduled(cron = "${abdm.housekeeping.consent-cron:0 5 * * * *}", zone = "Asia/Kolkata")
    public void expireConsentsAndEraseRecords() {
        runAsOrg("consent expiry and record erase", hiuData::expireAndErase);
        runAsOrg("retry of parked consent updates", ekaHiu::retryParkedUpdates);
    }

    // HIP_INTI_LINK_505: visits queued on save (AbdmVisitQueue) are linked through Eka, patient by patient, under every
    // HIP ID of the organisation; each patient once, however many of its HIP IDs it has visits waiting under.
    @Scheduled(cron = "${abdm.housekeeping.link-cron:0 */5 * * * *}", zone = "Asia/Kolkata")
    public void linkQueuedVisits() {
        runAsOrg("linking of queued visits", () -> {
            Map<String, List<String>> waiting = new LinkedHashMap<>();
            settings.find().ifPresent(orgSettings -> {
                for (String hipId : orgSettings.getHipIds()) {
                    for (String subjectUuid : hipStore.subjectsToLink(hipId, LINK_BATCH)) {
                        waiting.computeIfAbsent(subjectUuid, s -> new ArrayList<>()).add(hipId);
                    }
                }
            });
            waiting.forEach((subjectUuid, hipIds) -> {
                try {
                    ekaHip.linkCareContexts(subjectUuid, true);
                } catch (RuntimeException e) {
                    hipIds.forEach(hipId -> hipStore.failQueued(subjectUuid, hipId, e.getMessage()));
                    logger.info("ABDM: visits of subject {} not linked: {}", subjectUuid, e.getMessage());
                }
            });
        });
    }

    private void runAsOrg(String what, Runnable work) {
        if (!StringUtils.hasText(cfg.getSystemUser())) return;
        try {
            orgContext.runAsOrg(work);
        } catch (RuntimeException e) {
            logger.warn("ABDM {} failed: {}", what, e.getClass().getSimpleName());
        }
    }
}
