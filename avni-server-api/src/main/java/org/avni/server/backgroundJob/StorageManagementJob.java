package org.avni.server.backgroundJob;

import jakarta.persistence.EntityManager;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.ArchivalConfig;
import org.avni.server.domain.Organisation;
import org.avni.server.service.ArchivalConfigService;
import org.avni.server.service.StorageManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageManagementJob {
    private static final Logger logger = LoggerFactory.getLogger(StorageManagementJob.class);
    private final StorageManagementService storageManagementService;
    private final ArchivalConfigService archivalConfigService;
    private final OrganisationRepository organisationRepository;
    private final EntityManager entityManager;

    @Autowired
    public StorageManagementJob(StorageManagementService storageManagementService, ArchivalConfigService archivalConfigService, OrganisationRepository organisationRepository, EntityManager entityManager) {
        this.storageManagementService = storageManagementService;
        this.archivalConfigService = archivalConfigService;
        this.organisationRepository = organisationRepository;
        this.entityManager = entityManager;
    }

    // this method runs without organisation context
    @Scheduled(cron = "${avni.job.storagemanagement.cron}", zone = "Asia/Kolkata")
    public void manage() {
        logger.info("Starting archival job. " );
        List<ArchivalConfig> archivalConfigs = this.archivalConfigService.getAllArchivalConfigs();
        for (ArchivalConfig archivalConfig : archivalConfigs) {
            this.markSyncDisabled(archivalConfig);
        }
        logger.info("Completed nightly archival job");
    }

    private void markSyncDisabled(ArchivalConfig archivalConfig) {
//        these work only with Job test, so commented. had added to verify whether indeed new transaction is created
//        assert !TransactionSynchronizationManager.isActualTransactionActive();
        Organisation organisation = organisationRepository.findOne(archivalConfig.getOrganisationId());
        List<Long> subjectIds = storageManagementService.getNextSubjectIds(archivalConfig);
        while (!subjectIds.isEmpty()) {
            List<Long> previousSubjectIds = subjectIds;
            storageManagementService.markSyncDisabled(subjectIds, organisation);
            subjectIds = storageManagementService.getNextSubjectIds(archivalConfig);
            if (subjectIds.equals(previousSubjectIds)) {
                logger.info("Same subject ids retrieved again: {}", archivalConfig.getUuid());
                throw new RuntimeException("Same subject ids retrieved again");
            }
        }
//        assert !TransactionSynchronizationManager.isActualTransactionActive();
    }
}
