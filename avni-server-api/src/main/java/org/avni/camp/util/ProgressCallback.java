package org.avni.camp.util;

/**
 * Callback interface for tracking progress of sync operations.
 * Provides updates on the current progress and status messages.
 */
public interface ProgressCallback {
    
    /**
     * Called when the total number of items to process is calculated
     */
    void onTotalItemsCalculated(int totalItems);
    
    /**
     * Called to update progress
     * 
     * @param currentProgress Current number of processed items
     * @param totalItems Total number of items to process
     * @param statusMessage Current status message
     */
    void onProgress(int currentProgress, int totalItems, String statusMessage);
    
    /**
     * Called when a specific entity type processing is completed
     * 
     * @param entityType The entity type that was processed
     * @param totalPages Total number of pages for this entity type
     * @param currentPage Current page number processed
     */
    void onEntityProgress(String entityType, int totalPages, int currentPage);
    
    /**
     * Called when sync operation is completed
     */
    void onCompleted();
    
    /**
     * Called when sync operation fails
     * 
     * @param error The error that occurred
     */
    void onError(Throwable error);
    
    /**
     * Simple implementation that does nothing
     */
    ProgressCallback NOOP = new ProgressCallback() {
        @Override
        public void onTotalItemsCalculated(int totalItems) {}
        
        @Override
        public void onProgress(int currentProgress, int totalItems, String statusMessage) {}
        
        @Override
        public void onEntityProgress(String entityType, int totalPages, int currentPage) {}
        
        @Override
        public void onCompleted() {}
        
        @Override
        public void onError(Throwable error) {}
    };
    
    /**
     * Console logging implementation for debugging
     */
    class ConsoleProgressCallback implements ProgressCallback {
        private int lastReportedPercentage = -1;
        
        @Override
        public void onTotalItemsCalculated(int totalItems) {
            System.out.println("Total items to process: " + totalItems);
        }
        
        @Override
        public void onProgress(int currentProgress, int totalItems, String statusMessage) {
            if (totalItems > 0) {
                int percentage = (int) ((currentProgress * 100.0) / totalItems);
                if (percentage != lastReportedPercentage && percentage % 10 == 0) {
                    System.out.printf("Progress: %d%% (%d/%d) - %s%n", 
                        percentage, currentProgress, totalItems, statusMessage);
                    lastReportedPercentage = percentage;
                }
            } else {
                System.out.println("Progress: " + statusMessage);
            }
        }
        
        @Override
        public void onEntityProgress(String entityType, int totalPages, int currentPage) {
            if (totalPages > 1) {
                System.out.printf("Processing %s: page %d of %d%n", entityType, currentPage + 1, totalPages);
            }
        }
        
        @Override
        public void onCompleted() {
            System.out.println("Sync completed successfully!");
        }
        
        @Override
        public void onError(Throwable error) {
            System.err.println("Sync failed: " + error.getMessage());
        }
    }
}