package org.avni.server.backgroundJob;

import jakarta.persistence.EntityManager;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.StorageManagementConfig;
import org.avni.server.domain.Organisation;
import org.avni.server.service.StorageManagementConfigService;
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
    private final StorageManagementConfigService storageManagementConfigService;
    private final OrganisationRepository organisationRepository;
    private final EntityManager entityManager;

    @Autowired
    public StorageManagementJob(StorageManagementService storageManagementService, StorageManagementConfigService storageManagementConfigService, OrganisationRepository organisationRepository, EntityManager entityManager) {
        this.storageManagementService = storageManagementService;
        this.storageManagementConfigService = storageManagementConfigService;
        this.organisationRepository = organisationRepository;
        this.entityManager = entityManager;
    }

    // this method runs without organisation context
    @Scheduled(cron = "${avni.job.storagemanagement.cron}", zone = "Asia/Kolkata")
    public void manage() {
        logger.info("Starting storage management job." );
        List<StorageManagementConfig> storageManagementConfigs = this.storageManagementConfigService.getAllStorageManagementConfigs();
        for (StorageManagementConfig storageManagementConfig : storageManagementConfigs) {
            this.markSyncDisabled(storageManagementConfig);
        }
        logger.info("Completed storage management job.");
    }

    private void markSyncDisabled(StorageManagementConfig storageManagementConfig) {
//        these work only with Job test, so commented. had added to verify whether indeed new transaction is created
//        assert !TransactionSynchronizationManager.isActualTransactionActive();
        Organisation organisation = organisationRepository.findOne(storageManagementConfig.getOrganisationId());
        try {
            List<Long> subjectIds = storageManagementService.getNextSubjectIds(storageManagementConfig);
            while (!subjectIds.isEmpty()) {
                logger.info("Running for: {}. Current batch size: {}", organisation.getDbUser(), subjectIds.size());
                List<Long> previousSubjectIds = subjectIds;
                storageManagementService.markSyncDisabled(subjectIds, organisation);
                subjectIds = storageManagementService.getNextSubjectIds(storageManagementConfig);
                if (subjectIds.equals(previousSubjectIds)) {
                    logger.info("Same subject ids retrieved again: {}", storageManagementConfig.getUuid());
                    throw new RuntimeException("Same subject ids retrieved again");
                }
            }
        } catch (Exception e) {
            String msg = String.format("Failed to mark sync disabled for: %s", organisation.getDbUser());
            logger.error(msg, e);
        }
//        assert !TransactionSynchronizationManager.isActualTransactionActive();
    }
}
