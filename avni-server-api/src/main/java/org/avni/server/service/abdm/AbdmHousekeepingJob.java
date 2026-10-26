package org.avni.server.service.abdm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

// DEMO-ONLY abdm-hackathon: never merged.
// Scheduled ABDM upkeep, as the bridge's one organisation (AbdmOrgContext). Without abdm.system-user nothing runs.
@Component
public class AbdmHousekeepingJob {
    private static final Logger logger = LoggerFactory.getLogger(AbdmHousekeepingJob.class);

    private final AbdmConfig cfg;
    private final AbdmOrgContext orgContext;
    private final AbdmMessageLog messageLog;
    private final org.avni.server.service.abdm.hiu.AbdmHiuDataService hiuData;
    private final int messageRetentionDays;

    @Autowired
    public AbdmHousekeepingJob(AbdmConfig cfg, AbdmOrgContext orgContext, AbdmMessageLog messageLog,
                               org.avni.server.service.abdm.hiu.AbdmHiuDataService hiuData,
                               @Value("${abdm.message-retention-days:30}") int messageRetentionDays) {
        this.hiuData = hiuData;
        this.cfg = cfg;
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
