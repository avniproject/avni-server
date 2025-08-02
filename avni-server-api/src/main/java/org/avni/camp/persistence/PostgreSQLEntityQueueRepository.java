package org.avni.camp.persistence;

import org.avni.camp.model.EntityQueue;
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
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL implementation of EntityQueueRepository.
 * Handles CRUD operations for entity queue items in PostgreSQL.
 */
public class PostgreSQLEntityQueueRepository implements EntityQueueRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLEntityQueueRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public PostgreSQLEntityQueueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void save(EntityQueue entityQueue) {
        if (entityQueue == null) {
            logger.warn("Attempted to save null EntityQueue");
            return;
        }
        
        logger.debug("Saving EntityQueue: {}", entityQueue.getUuid());
        
        try {
            String sql = """
                INSERT INTO entity_queue (uuid, entity_uuid, entity_type, action, created_date_time, processed)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET
                    entity_uuid = EXCLUDED.entity_uuid,
                    entity_type = EXCLUDED.entity_type,
                    action = EXCLUDED.action,
                    created_date_time = EXCLUDED.created_date_time,
                    processed = EXCLUDED.processed
                """;
            
            jdbcTemplate.update(sql,
                entityQueue.getUuid(),
                entityQueue.getEntityUuid(),
                entityQueue.getEntityType(),
                entityQueue.getAction() != null ? entityQueue.getAction().toString() : null,
                entityQueue.getCreatedDateTime() != null ? Timestamp.valueOf(entityQueue.getCreatedDateTime()) : null,
                entityQueue.isProcessed()
            );
            
            logger.debug("Successfully saved EntityQueue: {}", entityQueue.getUuid());
            
        } catch (DataAccessException e) {
            logger.error("Error saving EntityQueue: {}", entityQueue.getUuid(), e);
            throw new RuntimeException("Failed to save EntityQueue: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<EntityQueue> findAll() {
        logger.debug("Finding all EntityQueue items");
        
        try {
            String sql = "SELECT * FROM entity_queue ORDER BY created_date_time";
            List<EntityQueue> results = jdbcTemplate.query(sql, new EntityQueueRowMapper());
            
            logger.debug("Found {} EntityQueue items", results.size());
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding all EntityQueue items", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<EntityQueue> findByEntityType(String entityType) {
        if (entityType == null) {
            return new ArrayList<>();
        }
        
        logger.debug("Finding EntityQueue items by entityType: {}", entityType);
        
        try {
            String sql = "SELECT * FROM entity_queue WHERE entity_type = ? ORDER BY created_date_time";
            List<EntityQueue> results = jdbcTemplate.query(sql, new EntityQueueRowMapper(), entityType);
            
            logger.debug("Found {} EntityQueue items for entityType: {}", results.size(), entityType);
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding EntityQueue items by entityType: {}", entityType, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public EntityQueue findByEntityUuid(String entityUuid) {
        if (entityUuid == null) {
            return null;
        }
        
        logger.debug("Finding EntityQueue by entityUuid: {}", entityUuid);
        
        try {
            String sql = "SELECT * FROM entity_queue WHERE entity_uuid = ?";
            
            return jdbcTemplate.queryForObject(sql, new EntityQueueRowMapper(), entityUuid);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No EntityQueue found for entityUuid: {}", entityUuid);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding EntityQueue by entityUuid: {}", entityUuid, e);
            return null;
        }
    }
    
    @Override
    public EntityQueue findByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        
        logger.debug("Finding EntityQueue by uuid: {}", uuid);
        
        try {
            String sql = "SELECT * FROM entity_queue WHERE uuid = ?";
            
            return jdbcTemplate.queryForObject(sql, new EntityQueueRowMapper(), uuid);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No EntityQueue found for uuid: {}", uuid);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding EntityQueue by uuid: {}", uuid, e);
            return null;
        }
    }
    
    @Override
    public List<String> findDistinctEntityTypes() {
        logger.debug("Finding distinct entity types");
        
        try {
            String sql = "SELECT DISTINCT entity_type FROM entity_queue ORDER BY entity_type";
            List<String> entityTypes = jdbcTemplate.queryForList(sql, String.class);
            
            logger.debug("Found {} distinct entity types", entityTypes.size());
            return entityTypes;
            
        } catch (DataAccessException e) {
            logger.error("Error finding distinct entity types", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void delete(EntityQueue entityQueue) {
        if (entityQueue == null || entityQueue.getUuid() == null) {
            logger.warn("Attempted to delete null EntityQueue or EntityQueue with null uuid");
            return;
        }
        
        logger.debug("Deleting EntityQueue: {}", entityQueue.getUuid());
        
        try {
            String sql = "DELETE FROM entity_queue WHERE uuid = ?";
            int deleted = jdbcTemplate.update(sql, entityQueue.getUuid());
            
            if (deleted > 0) {
                logger.debug("Successfully deleted EntityQueue: {}", entityQueue.getUuid());
            } else {
                logger.warn("No EntityQueue found with uuid: {} for deletion", entityQueue.getUuid());
            }
        } catch (DataAccessException e) {
            logger.error("Error deleting EntityQueue: {}", entityQueue.getUuid(), e);
            throw new RuntimeException("Failed to delete EntityQueue: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteByEntityUuid(String entityUuid) {
        if (entityUuid == null) {
            logger.warn("Attempted to delete EntityQueue with null entityUuid");
            return;
        }
        
        logger.debug("Deleting EntityQueue by entityUuid: {}", entityUuid);
        
        try {
            String sql = "DELETE FROM entity_queue WHERE entity_uuid = ?";
            int deleted = jdbcTemplate.update(sql, entityUuid);
            logger.debug("Deleted {} EntityQueue items for entityUuid: {}", deleted, entityUuid);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting EntityQueue by entityUuid: {}", entityUuid, e);
            throw new RuntimeException("Failed to delete EntityQueue by entityUuid: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteByEntityType(String entityType) {
        if (entityType == null) {
            logger.warn("Attempted to delete EntityQueue with null entityType");
            return;
        }
        
        logger.debug("Deleting EntityQueue by entityType: {}", entityType);
        
        try {
            String sql = "DELETE FROM entity_queue WHERE entity_type = ?";
            int deleted = jdbcTemplate.update(sql, entityType);
            logger.debug("Deleted {} EntityQueue items for entityType: {}", deleted, entityType);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting EntityQueue by entityType: {}", entityType, e);
            throw new RuntimeException("Failed to delete EntityQueue by entityType: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteAll() {
        logger.debug("Deleting all EntityQueue items");
        
        try {
            String sql = "DELETE FROM entity_queue";
            int deleted = jdbcTemplate.update(sql);
            logger.debug("Deleted {} EntityQueue items", deleted);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting all EntityQueue items", e);
            throw new RuntimeException("Failed to delete all EntityQueue items: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean existsByEntityUuid(String entityUuid) {
        if (entityUuid == null) {
            return false;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM entity_queue WHERE entity_uuid = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, entityUuid);
            return count != null && count > 0;
            
        } catch (DataAccessException e) {
            logger.error("Error checking existence by entityUuid: {}", entityUuid, e);
            return false;
        }
    }
    
    @Override
    public int countByEntityType(String entityType) {
        if (entityType == null) {
            return 0;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM entity_queue WHERE entity_type = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, entityType);
            return count != null ? count : 0;
            
        } catch (DataAccessException e) {
            logger.error("Error counting EntityQueue by entityType: {}", entityType, e);
            return 0;
        }
    }
    
    @Override
    public int countTotal() {
        try {
            String sql = "SELECT COUNT(*) FROM entity_queue";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
            
        } catch (DataAccessException e) {
            logger.error("Error counting total EntityQueue items", e);
            return 0;
        }
    }
    
    /**
     * RowMapper for converting ResultSet rows to EntityQueue objects
     */
    private static class EntityQueueRowMapper implements RowMapper<EntityQueue> {
        
        @Override
        public EntityQueue mapRow(ResultSet rs, int rowNum) throws SQLException {
            EntityQueue entityQueue = new EntityQueue();
            
            entityQueue.setUuid(rs.getString("uuid"));
            entityQueue.setEntityUuid(rs.getString("entity_uuid"));
            entityQueue.setEntityType(rs.getString("entity_type"));
            
            String actionStr = rs.getString("action");
            if (actionStr != null) {
                entityQueue.setAction(EntityQueue.Action.valueOf(actionStr));
            }
            
            Timestamp createdDateTime = rs.getTimestamp("created_date_time");
            if (createdDateTime != null) {
                entityQueue.setCreatedDateTime(createdDateTime.toLocalDateTime());
            }
            
            entityQueue.setProcessed(rs.getBoolean("processed"));
            
            return entityQueue;
        }
    }
}