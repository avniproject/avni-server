package org.avni.server.scheduler;

import org.avni.server.service.ArchivalJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ArchivalScheduler {
    private static final Logger logger = LoggerFactory.getLogger(ArchivalScheduler.class);

    private final ArchivalJobService archivalJobService;

    public ArchivalScheduler(ArchivalJobService archivalJobService) {
        this.archivalJobService = archivalJobService;
    }
    //for testing a controller, remove batch config
    /// listener not req
    @Scheduled(cron = "0 0 2 * * *") // Run at 2 AM every day
    public void runNightlyArchival() {
        try {
            logger.info("Starting nightly archival job");
            archivalJobService.triggerArchivalJob();
            logger.info("Completed nightly archival job");
        } catch (Exception e) {
            logger.error("Error in nightly archival job", e);
        }
    }
}