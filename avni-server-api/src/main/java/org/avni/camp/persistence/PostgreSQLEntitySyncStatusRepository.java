package org.avni.camp.persistence;

import org.avni.camp.model.EntitySyncStatus;
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

/**
 * PostgreSQL implementation of EntitySyncStatusRepository.
 * Handles CRUD operations for entity synchronization status tracking in PostgreSQL.
 */
public class PostgreSQLEntitySyncStatusRepository implements EntitySyncStatusRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLEntitySyncStatusRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public PostgreSQLEntitySyncStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void save(EntitySyncStatus entitySyncStatus) {
        if (entitySyncStatus == null) {
            logger.warn("Attempted to save null EntitySyncStatus");
            return;
        }
        
        logger.debug("Saving EntitySyncStatus: {}", entitySyncStatus.getUuid());
        
        try {
            String sql = """
                INSERT INTO entity_sync_status (uuid, entity_name, entity_type_uuid, loaded_since, last_modified_date_time, is_voided)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET
                    entity_name = EXCLUDED.entity_name,
                    entity_type_uuid = EXCLUDED.entity_type_uuid,
                    loaded_since = EXCLUDED.loaded_since,
                    last_modified_date_time = EXCLUDED.last_modified_date_time,
                    is_voided = EXCLUDED.is_voided
                """;
            
            jdbcTemplate.update(sql,
                entitySyncStatus.getUuid(),
                entitySyncStatus.getEntityName(),
                entitySyncStatus.getEntityTypeUuid(),
                Timestamp.valueOf(entitySyncStatus.getLoadedSince()),
                Timestamp.valueOf(entitySyncStatus.getLastModifiedDateTime()),
                entitySyncStatus.isVoided()
            );
            
            logger.debug("Successfully saved EntitySyncStatus: {}", entitySyncStatus.getUuid());
            
        } catch (DataAccessException e) {
            logger.error("Error saving EntitySyncStatus: {}", entitySyncStatus.getUuid(), e);
            throw new RuntimeException("Failed to save EntitySyncStatus: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<EntitySyncStatus> findAll() {
        logger.debug("Finding all EntitySyncStatus records");
        
        try {
            String sql = "SELECT * FROM entity_sync_status WHERE NOT COALESCE(is_voided, false) ORDER BY last_modified_date_time";
            List<EntitySyncStatus> results = jdbcTemplate.query(sql, new EntitySyncStatusRowMapper());
            
            logger.debug("Found {} EntitySyncStatus records", results.size());
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding all EntitySyncStatus records", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public EntitySyncStatus findByEntityNameAndTypeUuid(String entityName, String entityTypeUuid) {
        if (entityName == null) {
            return null;
        }
        
        logger.debug("Finding EntitySyncStatus by entityName: {} and typeUuid: {}", entityName, entityTypeUuid);
        
        try {
            String sql = "SELECT * FROM entity_sync_status WHERE entity_name = ? AND entity_type_uuid = ? AND NOT COALESCE(is_voided, false)";
            
            return jdbcTemplate.queryForObject(sql, new EntitySyncStatusRowMapper(), entityName, entityTypeUuid);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No EntitySyncStatus found for entityName: {} and typeUuid: {}", entityName, entityTypeUuid);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding EntitySyncStatus by entityName: {} and typeUuid: {}", entityName, entityTypeUuid, e);
            return null;
        }
    }
    
    @Override
    public EntitySyncStatus findByEntityName(String entityName) {
        if (entityName == null) {
            return null;
        }
        
        logger.debug("Finding EntitySyncStatus by entityName: {}", entityName);
        
        try {
            String sql = "SELECT * FROM entity_sync_status WHERE entity_name = ? AND entity_type_uuid IS NULL AND NOT COALESCE(is_voided, false) LIMIT 1";
            
            return jdbcTemplate.queryForObject(sql, new EntitySyncStatusRowMapper(), entityName);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No EntitySyncStatus found for entityName: {}", entityName);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding EntitySyncStatus by entityName: {}", entityName, e);
            return null;
        }
    }
    
    @Override
    public List<EntitySyncStatus> findAllByEntityName(String entityName) {
        if (entityName == null) {
            return new ArrayList<>();
        }
        
        logger.debug("Finding all EntitySyncStatus by entityName: {}", entityName);
        
        try {
            String sql = "SELECT * FROM entity_sync_status WHERE entity_name = ? AND NOT COALESCE(is_voided, false) ORDER BY last_modified_date_time";
            List<EntitySyncStatus> results = jdbcTemplate.query(sql, new EntitySyncStatusRowMapper(), entityName);
            
            logger.debug("Found {} EntitySyncStatus records for entityName: {}", results.size(), entityName);
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding EntitySyncStatus by entityName: {}", entityName, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<String> findDistinctEntityNames() {
        logger.debug("Finding distinct entity names");
        
        try {
            String sql = "SELECT DISTINCT entity_name FROM entity_sync_status WHERE NOT COALESCE(is_voided, false) ORDER BY entity_name";
            List<String> entityNames = jdbcTemplate.queryForList(sql, String.class);
            
            logger.debug("Found {} distinct entity names", entityNames.size());
            return entityNames;
            
        } catch (DataAccessException e) {
            logger.error("Error finding distinct entity names", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public EntitySyncStatus findByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        
        logger.debug("Finding EntitySyncStatus by uuid: {}", uuid);
        
        try {
            String sql = "SELECT * FROM entity_sync_status WHERE uuid = ?";
            
            return jdbcTemplate.queryForObject(sql, new EntitySyncStatusRowMapper(), uuid);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No EntitySyncStatus found for uuid: {}", uuid);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding EntitySyncStatus by uuid: {}", uuid, e);
            return null;
        }
    }
    
    @Override
    public void delete(EntitySyncStatus entitySyncStatus) {
        if (entitySyncStatus == null || entitySyncStatus.getUuid() == null) {
            logger.warn("Attempted to delete null EntitySyncStatus or EntitySyncStatus with null uuid");
            return;
        }
        
        logger.debug("Soft deleting EntitySyncStatus: {}", entitySyncStatus.getUuid());
        
        try {
            String sql = "UPDATE entity_sync_status SET is_voided = true, last_modified_date_time = NOW() WHERE uuid = ?";
            int updated = jdbcTemplate.update(sql, entitySyncStatus.getUuid());
            
            if (updated > 0) {
                logger.debug("Successfully soft deleted EntitySyncStatus: {}", entitySyncStatus.getUuid());
            } else {
                logger.warn("No EntitySyncStatus found with uuid: {} for deletion", entitySyncStatus.getUuid());
            }
        } catch (DataAccessException e) {
            logger.error("Error soft deleting EntitySyncStatus: {}", entitySyncStatus.getUuid(), e);
            throw new RuntimeException("Failed to delete EntitySyncStatus: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteByEntityNameAndTypeUuids(String entityName, List<String> entityTypeUuids) {
        if (entityName == null || entityTypeUuids == null || entityTypeUuids.isEmpty()) {
            logger.warn("Invalid parameters for deleteByEntityNameAndTypeUuids");
            return;
        }
        
        logger.debug("Soft deleting EntitySyncStatus by entityName: {} and typeUuids: {}", entityName, entityTypeUuids);
        
        try {
            String placeholders = String.join(",", entityTypeUuids.stream().map(uuid -> "?").toList());
            String sql = "UPDATE entity_sync_status SET is_voided = true, last_modified_date_time = NOW() " +
                        "WHERE entity_name = ? AND entity_type_uuid IN (" + placeholders + ")";
            
            List<Object> params = new ArrayList<>();
            params.add(entityName);
            params.addAll(entityTypeUuids);
            
            int updated = jdbcTemplate.update(sql, params.toArray());
            logger.debug("Soft deleted {} EntitySyncStatus records", updated);
            
        } catch (DataAccessException e) {
            logger.error("Error soft deleting EntitySyncStatus by entityName: {} and typeUuids: {}", entityName, entityTypeUuids, e);
            throw new RuntimeException("Failed to delete EntitySyncStatus records: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteAll() {
        logger.debug("Soft deleting all EntitySyncStatus records");
        
        try {
            String sql = "UPDATE entity_sync_status SET is_voided = true, last_modified_date_time = NOW()";
            int updated = jdbcTemplate.update(sql);
            logger.debug("Soft deleted {} EntitySyncStatus records", updated);
            
        } catch (DataAccessException e) {
            logger.error("Error soft deleting all EntitySyncStatus records", e);
            throw new RuntimeException("Failed to delete all EntitySyncStatus records: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean existsByEntityNameAndTypeUuid(String entityName, String entityTypeUuid) {
        if (entityName == null) {
            return false;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM entity_sync_status WHERE entity_name = ? AND entity_type_uuid = ? AND NOT COALESCE(is_voided, false)";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, entityName, entityTypeUuid);
            return count != null && count > 0;
            
        } catch (DataAccessException e) {
            logger.error("Error checking existence by entityName: {} and typeUuid: {}", entityName, entityTypeUuid, e);
            return false;
        }
    }
    
    @Override
    public int countTotal() {
        try {
            String sql = "SELECT COUNT(*) FROM entity_sync_status WHERE NOT COALESCE(is_voided, false)";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
            
        } catch (DataAccessException e) {
            logger.error("Error counting total EntitySyncStatus records", e);
            return 0;
        }
    }
    
    @Override
    public void initializeMissingSyncStatuses(List<String> entityNames) {
        if (entityNames == null || entityNames.isEmpty()) {
            return;
        }
        
        logger.debug("Initializing missing sync statuses for entities: {}", entityNames);
        
        try {
            for (String entityName : entityNames) {
                // Check if sync status already exists for this entity
                EntitySyncStatus existing = findByEntityName(entityName);
                if (existing == null) {
                    EntitySyncStatus newStatus = new EntitySyncStatus();
                    newStatus.setEntityName(entityName);
                    newStatus.setLoadedSince(EntitySyncStatus.REALLY_OLD_DATE);
                    save(newStatus);
                    logger.debug("Initialized sync status for entity: {}", entityName);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error initializing missing sync statuses", e);
            throw new RuntimeException("Failed to initialize missing sync statuses: " + e.getMessage(), e);
        }
    }
    
    /**
     * RowMapper for converting ResultSet rows to EntitySyncStatus objects
     */
    private static class EntitySyncStatusRowMapper implements RowMapper<EntitySyncStatus> {
        
        @Override
        public EntitySyncStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
            EntitySyncStatus entitySyncStatus = new EntitySyncStatus();
            
            entitySyncStatus.setUuid(rs.getString("uuid"));
            entitySyncStatus.setEntityName(rs.getString("entity_name"));
            entitySyncStatus.setEntityTypeUuid(rs.getString("entity_type_uuid"));
            
            Timestamp loadedSince = rs.getTimestamp("loaded_since");
            if (loadedSince != null) {
                entitySyncStatus.setLoadedSince(loadedSince.toLocalDateTime());
            }
            
            Timestamp lastModified = rs.getTimestamp("last_modified_date_time");
            if (lastModified != null) {
                entitySyncStatus.setLastModifiedDateTime(lastModified.toLocalDateTime());
            }
            
            entitySyncStatus.setVoided(rs.getBoolean("is_voided"));
            
            return entitySyncStatus;
        }
    }
}