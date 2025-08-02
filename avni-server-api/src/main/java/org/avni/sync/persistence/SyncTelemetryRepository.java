package org.avni.sync.persistence;

import org.avni.sync.model.SyncTelemetry;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for managing SyncTelemetry persistence.
 * Handles CRUD operations for sync operation telemetry and statistics.
 */
public interface SyncTelemetryRepository {
    
    /**
     * Saves or updates sync telemetry
     */
    void save(SyncTelemetry syncTelemetry);
    
    /**
     * Finds all sync telemetry records
     */
    List<SyncTelemetry> findAll();
    
    /**
     * Finds sync telemetry by UUID
     */
    SyncTelemetry findByUuid(String uuid);
    
    /**
     * Finds sync telemetry records by sync source
     */
    List<SyncTelemetry> findBySyncSource(SyncTelemetry.SyncSource syncSource);
    
    /**
     * Finds sync telemetry records within a date range
     */
    List<SyncTelemetry> findBySyncStartTimeBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Finds completed sync telemetry records
     */
    List<SyncTelemetry> findCompleted();
    
    /**
     * Finds failed sync telemetry records
     */
    List<SyncTelemetry> findFailed();
    
    /**
     * Gets the latest completed sync (any type)
     */
    SyncTelemetry getLatestCompletedSync();
    
    /**
     * Gets the latest completed full sync (manual or automatic, not upload-only)
     */
    SyncTelemetry getLatestCompletedFullSync();
    
    /**
     * Gets the latest sync telemetry record
     */
    SyncTelemetry getLatestSync();
    
    /**
     * Finds sync telemetry records by device ID
     */
    List<SyncTelemetry> findByDeviceId(String deviceId);
    
    /**
     * Deletes sync telemetry record
     */
    void delete(SyncTelemetry syncTelemetry);
    
    /**
     * Deletes sync telemetry records older than specified date
     */
    void deleteOlderThan(LocalDateTime cutoffDate);
    
    /**
     * Deletes all sync telemetry records
     */
    void deleteAll();
    
    /**
     * Counts sync telemetry records by sync source
     */
    int countBySyncSource(SyncTelemetry.SyncSource syncSource);
    
    /**
     * Counts completed syncs within a date range
     */
    int countCompletedSyncsInRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Counts failed syncs within a date range
     */
    int countFailedSyncsInRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Gets average sync duration for completed syncs
     */
    Double getAverageSyncDurationMillis();
    
    /**
     * Gets sync statistics for a specific time period
     */
    SyncStatistics getSyncStatistics(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Statistics container for sync telemetry analysis
     */
    class SyncStatistics {
        private int totalSyncs;
        private int completedSyncs;
        private int failedSyncs;
        private double averageDurationMillis;
        private int totalEntitiesPulled;
        private int totalEntitiesPushed;
        private int totalMediaPushed;
        
        public SyncStatistics() {}
        
        public SyncStatistics(int totalSyncs, int completedSyncs, int failedSyncs, 
                             double averageDurationMillis, int totalEntitiesPulled, 
                             int totalEntitiesPushed, int totalMediaPushed) {
            this.totalSyncs = totalSyncs;
            this.completedSyncs = completedSyncs;
            this.failedSyncs = failedSyncs;
            this.averageDurationMillis = averageDurationMillis;
            this.totalEntitiesPulled = totalEntitiesPulled;
            this.totalEntitiesPushed = totalEntitiesPushed;
            this.totalMediaPushed = totalMediaPushed;
        }
        
        // Getters and setters
        public int getTotalSyncs() { return totalSyncs; }
        public void setTotalSyncs(int totalSyncs) { this.totalSyncs = totalSyncs; }
        
        public int getCompletedSyncs() { return completedSyncs; }
        public void setCompletedSyncs(int completedSyncs) { this.completedSyncs = completedSyncs; }
        
        public int getFailedSyncs() { return failedSyncs; }
        public void setFailedSyncs(int failedSyncs) { this.failedSyncs = failedSyncs; }
        
        public double getAverageDurationMillis() { return averageDurationMillis; }
        public void setAverageDurationMillis(double averageDurationMillis) { this.averageDurationMillis = averageDurationMillis; }
        
        public int getTotalEntitiesPulled() { return totalEntitiesPulled; }
        public void setTotalEntitiesPulled(int totalEntitiesPulled) { this.totalEntitiesPulled = totalEntitiesPulled; }
        
        public int getTotalEntitiesPushed() { return totalEntitiesPushed; }
        public void setTotalEntitiesPushed(int totalEntitiesPushed) { this.totalEntitiesPushed = totalEntitiesPushed; }
        
        public int getTotalMediaPushed() { return totalMediaPushed; }
        public void setTotalMediaPushed(int totalMediaPushed) { this.totalMediaPushed = totalMediaPushed; }
        
        public double getSuccessRate() {
            return totalSyncs > 0 ? (double) completedSyncs / totalSyncs * 100 : 0;
        }
        
        public double getFailureRate() {
            return totalSyncs > 0 ? (double) failedSyncs / totalSyncs * 100 : 0;
        }
        
        @Override
        public String toString() {
            return "SyncStatistics{" +
                    "totalSyncs=" + totalSyncs +
                    ", completedSyncs=" + completedSyncs +
                    ", failedSyncs=" + failedSyncs +
                    ", successRate=" + String.format("%.1f%%", getSuccessRate()) +
                    ", averageDurationMillis=" + averageDurationMillis +
                    ", totalEntitiesPulled=" + totalEntitiesPulled +
                    ", totalEntitiesPushed=" + totalEntitiesPushed +
                    ", totalMediaPushed=" + totalMediaPushed +
                    '}';
        }
    }
}