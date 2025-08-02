package org.avni.camp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API-specific progress callback implementation for sync operations.
 * Logs progress updates using SLF4J logger.
 */
public class ApiProgressCallback implements ProgressCallback {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiProgressCallback.class);
    
    @Override
    public void onTotalItemsCalculated(int totalItems) {
        logger.debug("Total items to process: {}", totalItems);
    }
    
    @Override
    public void onProgress(int currentProgress, int totalItems, String statusMessage) {
        int percentage = totalItems > 0 ? (currentProgress * 100) / totalItems : 0;
        logger.debug("Sync progress: {}% ({}/{}) - {}", percentage, currentProgress, totalItems, statusMessage);
    }
    
    @Override
    public void onEntityProgress(String entityType, int totalPages, int currentPage) {
        int percentage = totalPages > 0 ? (currentPage * 100) / totalPages : 0;
        logger.debug("Entity {} progress: {}% (page {}/{})", entityType, percentage, currentPage, totalPages);
    }
    
    @Override
    public void onCompleted() {
        logger.info("Sync operation completed successfully");
    }
    
    @Override
    public void onError(Throwable error) {
        logger.error("Sync operation failed", error);
    }
}
