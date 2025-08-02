package org.avni.camp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.camp.model.SyncTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL implementation of SyncTelemetryRepository.
 * Handles CRUD operations for sync telemetry tracking in PostgreSQL.
 */
public class PostgreSQLSyncTelemetryRepository implements SyncTelemetryRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLSyncTelemetryRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public PostgreSQLSyncTelemetryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void save(SyncTelemetry syncTelemetry) {
        if (syncTelemetry == null) {
            logger.warn("Attempted to save null SyncTelemetry");
            return;
        }
        
        logger.debug("Saving SyncTelemetry: {}", syncTelemetry.getUuid());
        
        try {
            String connectionInfoJson = null;
            if (syncTelemetry.getConnectionInfo() != null) {
                connectionInfoJson = objectMapper.writeValueAsString(syncTelemetry.getConnectionInfo());
            }
            
            String sql = """
                INSERT INTO sync_telemetry (
                    uuid, sync_source, device_id, sync_start_time, sync_end_time,
                    entity_push_completed, media_push_completed, entity_pull_completed,
                    total_entities_pulled, total_entities_pushed, total_media_pushed,
                    sync_status, sync_error_message, connection_info
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (uuid) DO UPDATE SET
                    sync_source = EXCLUDED.sync_source,
                    device_id = EXCLUDED.device_id,
                    sync_start_time = EXCLUDED.sync_start_time,
                    sync_end_time = EXCLUDED.sync_end_time,
                    entity_push_completed = EXCLUDED.entity_push_completed,
                    media_push_completed = EXCLUDED.media_push_completed,
                    entity_pull_completed = EXCLUDED.entity_pull_completed,
                    total_entities_pulled = EXCLUDED.total_entities_pulled,
                    total_entities_pushed = EXCLUDED.total_entities_pushed,
                    total_media_pushed = EXCLUDED.total_media_pushed,
                    sync_status = EXCLUDED.sync_status,
                    sync_error_message = EXCLUDED.sync_error_message,
                    connection_info = EXCLUDED.connection_info
                """;
            
            jdbcTemplate.update(sql,
                syncTelemetry.getUuid(),
                syncTelemetry.getSyncSource().toString(),
                syncTelemetry.getDeviceId(),
                syncTelemetry.getSyncStartTime() != null ? Timestamp.valueOf(syncTelemetry.getSyncStartTime()) : null,
                syncTelemetry.getSyncEndTime() != null ? Timestamp.valueOf(syncTelemetry.getSyncEndTime()) : null,
                syncTelemetry.isEntityPushCompleted(),
                syncTelemetry.isMediaPushCompleted(),
                syncTelemetry.isEntityPullCompleted(),
                syncTelemetry.getTotalEntitiesPulled(),
                syncTelemetry.getTotalEntitiesPushed(),
                syncTelemetry.getTotalMediaPushed(),
                syncTelemetry.getSyncStatus().toString(),
                syncTelemetry.getSyncErrorMessage(),
                connectionInfoJson
            );
            
            logger.debug("Successfully saved SyncTelemetry: {}", syncTelemetry.getUuid());
            
        } catch (Exception e) {
            logger.error("Error saving SyncTelemetry: {}", syncTelemetry.getUuid(), e);
            throw new RuntimeException("Failed to save SyncTelemetry: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<SyncTelemetry> findAll() {
        logger.debug("Finding all SyncTelemetry records");
        
        try {
            String sql = "SELECT * FROM sync_telemetry ORDER BY sync_start_time DESC";
            List<SyncTelemetry> results = jdbcTemplate.query(sql, new SyncTelemetryRowMapper());
            
            logger.debug("Found {} SyncTelemetry records", results.size());
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding all SyncTelemetry records", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public SyncTelemetry findByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        
        logger.debug("Finding SyncTelemetry by uuid: {}", uuid);
        
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE uuid = ?";
            
            return jdbcTemplate.queryForObject(sql, new SyncTelemetryRowMapper(), uuid);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No SyncTelemetry found for uuid: {}", uuid);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding SyncTelemetry by uuid: {}", uuid, e);
            return null;
        }
    }
    
    @Override
    public List<SyncTelemetry> findBySyncSource(SyncTelemetry.SyncSource syncSource) {
        if (syncSource == null) {
            return new ArrayList<>();
        }
        
        logger.debug("Finding SyncTelemetry by syncSource: {}", syncSource);
        
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE sync_source = ? ORDER BY sync_start_time DESC";
            List<SyncTelemetry> results = jdbcTemplate.query(sql, new SyncTelemetryRowMapper(), syncSource.toString());
            
            logger.debug("Found {} SyncTelemetry records for syncSource: {}", results.size(), syncSource);
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding SyncTelemetry by syncSource: {}", syncSource, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<SyncTelemetry> findBySyncStartTimeBetween(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return new ArrayList<>();
        }
        
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE sync_start_time BETWEEN ? AND ? ORDER BY sync_start_time DESC";
            return jdbcTemplate.query(sql, new SyncTelemetryRowMapper(), 
                Timestamp.valueOf(startTime), Timestamp.valueOf(endTime));
        } catch (DataAccessException e) {
            logger.error("Error finding SyncTelemetry by date range", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<SyncTelemetry> findCompleted() {
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE sync_status = ? ORDER BY sync_start_time DESC";
            return jdbcTemplate.query(sql, new SyncTelemetryRowMapper(), SyncTelemetry.SyncStatus.COMPLETED.toString());
        } catch (DataAccessException e) {
            logger.error("Error finding completed SyncTelemetry records", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<SyncTelemetry> findFailed() {
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE sync_status = ? ORDER BY sync_start_time DESC";
            return jdbcTemplate.query(sql, new SyncTelemetryRowMapper(), SyncTelemetry.SyncStatus.FAILED.toString());
        } catch (DataAccessException e) {
            logger.error("Error finding failed SyncTelemetry records", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public SyncTelemetry getLatestCompletedSync() {
        logger.debug("Finding latest completed sync");
        
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE sync_status = ? ORDER BY sync_end_time DESC LIMIT 1";
            
            return jdbcTemplate.queryForObject(sql, new SyncTelemetryRowMapper(), 
                SyncTelemetry.SyncStatus.COMPLETED.toString());
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No completed sync found");
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding latest completed sync", e);
            return null;
        }
    }
    
    @Override
    public SyncTelemetry getLatestCompletedFullSync() {
        logger.debug("Finding latest completed full sync");
        
        try {
            String sql = """
                SELECT * FROM sync_telemetry 
                WHERE sync_status = ? 
                AND entity_pull_completed = true 
                AND entity_push_completed = true
                ORDER BY sync_end_time DESC 
                LIMIT 1
                """;
            
            return jdbcTemplate.queryForObject(sql, new SyncTelemetryRowMapper(), 
                SyncTelemetry.SyncStatus.COMPLETED.toString());
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No completed full sync found");
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding latest completed full sync", e);
            return null;
        }
    }
    
    @Override
    public SyncTelemetry getLatestSync() {
        logger.debug("Finding latest sync");
        
        try {
            String sql = "SELECT * FROM sync_telemetry ORDER BY sync_start_time DESC LIMIT 1";
            
            return jdbcTemplate.queryForObject(sql, new SyncTelemetryRowMapper());
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No sync found");
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding latest sync", e);
            return null;
        }
    }
    
    @Override
    public List<SyncTelemetry> findByDeviceId(String deviceId) {
        if (deviceId == null) {
            return new ArrayList<>();
        }
        
        try {
            String sql = "SELECT * FROM sync_telemetry WHERE device_id = ? ORDER BY sync_start_time DESC";
            return jdbcTemplate.query(sql, new SyncTelemetryRowMapper(), deviceId);
        } catch (DataAccessException e) {
            logger.error("Error finding SyncTelemetry by deviceId: {}", deviceId, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void delete(SyncTelemetry syncTelemetry) {
        if (syncTelemetry == null || syncTelemetry.getUuid() == null) {
            return;
        }
        
        try {
            String sql = "DELETE FROM sync_telemetry WHERE uuid = ?";
            jdbcTemplate.update(sql, syncTelemetry.getUuid());
        } catch (DataAccessException e) {
            logger.error("Error deleting SyncTelemetry: {}", syncTelemetry.getUuid(), e);
            throw new RuntimeException("Failed to delete SyncTelemetry: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteOlderThan(LocalDateTime cutoffDate) {
        if (cutoffDate == null) {
            return;
        }
        
        try {
            String sql = "DELETE FROM sync_telemetry WHERE sync_start_time < ?";
            int deleted = jdbcTemplate.update(sql, Timestamp.valueOf(cutoffDate));
            logger.debug("Deleted {} old SyncTelemetry records", deleted);
        } catch (DataAccessException e) {
            logger.error("Error deleting old SyncTelemetry records", e);
            throw new RuntimeException("Failed to delete old SyncTelemetry records: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteAll() {
        try {
            String sql = "DELETE FROM sync_telemetry";
            int deleted = jdbcTemplate.update(sql);
            logger.debug("Deleted {} SyncTelemetry records", deleted);
        } catch (DataAccessException e) {
            logger.error("Error deleting all SyncTelemetry records", e);
            throw new RuntimeException("Failed to delete all SyncTelemetry records: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int countBySyncSource(SyncTelemetry.SyncSource syncSource) {
        if (syncSource == null) {
            return 0;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM sync_telemetry WHERE sync_source = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, syncSource.toString());
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            logger.error("Error counting SyncTelemetry by source: {}", syncSource, e);
            return 0;
        }
    }
    
    @Override
    public int countCompletedSyncsInRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM sync_telemetry WHERE sync_status = ? AND sync_start_time BETWEEN ? AND ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
                SyncTelemetry.SyncStatus.COMPLETED.toString(), 
                Timestamp.valueOf(startTime), 
                Timestamp.valueOf(endTime));
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            logger.error("Error counting completed syncs in range", e);
            return 0;
        }
    }
    
    @Override
    public int countFailedSyncsInRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM sync_telemetry WHERE sync_status = ? AND sync_start_time BETWEEN ? AND ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
                SyncTelemetry.SyncStatus.FAILED.toString(), 
                Timestamp.valueOf(startTime), 
                Timestamp.valueOf(endTime));
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            logger.error("Error counting failed syncs in range", e);
            return 0;
        }
    }
    
    @Override
    public Double getAverageSyncDurationMillis() {
        try {
            String sql = """
                SELECT AVG(EXTRACT(EPOCH FROM (sync_end_time - sync_start_time)) * 1000) 
                FROM sync_telemetry 
                WHERE sync_status = ? AND sync_end_time IS NOT NULL
                """;
            Double avg = jdbcTemplate.queryForObject(sql, Double.class, SyncTelemetry.SyncStatus.COMPLETED.toString());
            return avg != null ? avg : 0.0;
        } catch (DataAccessException e) {
            logger.error("Error calculating average sync duration", e);
            return 0.0;
        }
    }
    
    @Override
    public SyncStatistics getSyncStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            String sql = """
                SELECT 
                    COUNT(*) as total_syncs,
                    SUM(CASE WHEN sync_status = ? THEN 1 ELSE 0 END) as completed_syncs,
                    SUM(CASE WHEN sync_status = ? THEN 1 ELSE 0 END) as failed_syncs,
                    AVG(CASE WHEN sync_status = ? AND sync_end_time IS NOT NULL 
                        THEN EXTRACT(EPOCH FROM (sync_end_time - sync_start_time)) * 1000 
                        ELSE NULL END) as avg_duration,
                    SUM(total_entities_pulled) as total_entities_pulled,
                    SUM(total_entities_pushed) as total_entities_pushed,
                    SUM(total_media_pushed) as total_media_pushed
                FROM sync_telemetry 
                WHERE sync_start_time BETWEEN ? AND ?
                """;
            
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                return new SyncStatistics(
                    rs.getInt("total_syncs"),
                    rs.getInt("completed_syncs"),
                    rs.getInt("failed_syncs"),
                    rs.getDouble("avg_duration"),
                    rs.getInt("total_entities_pulled"),
                    rs.getInt("total_entities_pushed"),
                    rs.getInt("total_media_pushed")
                );
            }, 
            SyncTelemetry.SyncStatus.COMPLETED.toString(),
            SyncTelemetry.SyncStatus.FAILED.toString(),
            SyncTelemetry.SyncStatus.COMPLETED.toString(),
            Timestamp.valueOf(startTime), 
            Timestamp.valueOf(endTime));
            
        } catch (DataAccessException e) {
            logger.error("Error getting sync statistics", e);
            return new SyncStatistics(0, 0, 0, 0.0, 0, 0, 0);
        }
    }
    
    /**
     * RowMapper for converting ResultSet rows to SyncTelemetry objects
     */
    private class SyncTelemetryRowMapper implements RowMapper<SyncTelemetry> {
        
        @Override
        public SyncTelemetry mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                String syncSourceStr = rs.getString("sync_source");
                SyncTelemetry.SyncSource syncSource = SyncTelemetry.SyncSource.valueOf(syncSourceStr);
                String deviceId = rs.getString("device_id");
                
                SyncTelemetry syncTelemetry = new SyncTelemetry(syncSource, deviceId);
                syncTelemetry.setUuid(rs.getString("uuid"));
                
                Timestamp syncStartTime = rs.getTimestamp("sync_start_time");
                if (syncStartTime != null) {
                    syncTelemetry.setSyncStartTime(syncStartTime.toLocalDateTime());
                }
                
                Timestamp syncEndTime = rs.getTimestamp("sync_end_time");
                if (syncEndTime != null) {
                    syncTelemetry.setSyncEndTime(syncEndTime.toLocalDateTime());
                }
                
                syncTelemetry.setEntityPushCompleted(rs.getBoolean("entity_push_completed"));
                syncTelemetry.setMediaPushCompleted(rs.getBoolean("media_push_completed"));
                syncTelemetry.setEntityPullCompleted(rs.getBoolean("entity_pull_completed"));
                syncTelemetry.setTotalEntitiesPulled(rs.getInt("total_entities_pulled"));
                syncTelemetry.setTotalEntitiesPushed(rs.getInt("total_entities_pushed"));
                syncTelemetry.setTotalMediaPushed(rs.getInt("total_media_pushed"));
                
                String syncStatusStr = rs.getString("sync_status");
                if (syncStatusStr != null) {
                    syncTelemetry.setSyncStatus(syncStatusStr);
                }
                
                syncTelemetry.setSyncErrorMessage(rs.getString("sync_error_message"));
                
                String connectionInfoJson = rs.getString("connection_info");
                if (connectionInfoJson != null) {
                    try {
                        JsonNode connectionInfo = objectMapper.readTree(connectionInfoJson);
                        Map<String, Object> connectionInfoMap = objectMapper.convertValue(connectionInfo, 
                                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                        syncTelemetry.setConnectionInfo(connectionInfoMap);
                    } catch (Exception e) {
                        logger.warn("Error parsing connection info JSON for SyncTelemetry: {}", syncTelemetry.getUuid(), e);
                    }
                }
                
                return syncTelemetry;
                
            } catch (Exception e) {
                logger.error("Error mapping SyncTelemetry row", e);
                throw new SQLException("Error mapping SyncTelemetry row: " + e.getMessage(), e);
            }
        }
    }
}