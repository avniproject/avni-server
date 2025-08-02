package org.avni.camp.persistence;

import org.avni.camp.model.MediaQueue;
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
 * PostgreSQL implementation of MediaQueueRepository.
 * Handles CRUD operations for media queue items in PostgreSQL.
 */
public class PostgreSQLMediaQueueRepository implements MediaQueueRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLMediaQueueRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public PostgreSQLMediaQueueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void save(MediaQueue mediaQueue) {
        if (mediaQueue == null) {
            logger.warn("Attempted to save null MediaQueue");
            return;
        }
        
        logger.debug("Saving MediaQueue: {}", mediaQueue.getUuid());
        
        try {
            String sql = """
                INSERT INTO media_queue (
                    uuid, entity_uuid, entity_name, file_name, type, concept_uuid, 
                    entity_target_field, created_date_time, uploaded
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET
                    entity_uuid = EXCLUDED.entity_uuid,
                    entity_name = EXCLUDED.entity_name,
                    file_name = EXCLUDED.file_name,
                    type = EXCLUDED.type,
                    concept_uuid = EXCLUDED.concept_uuid,
                    entity_target_field = EXCLUDED.entity_target_field,
                    created_date_time = EXCLUDED.created_date_time,
                    uploaded = EXCLUDED.uploaded
                """;
            
            jdbcTemplate.update(sql,
                mediaQueue.getUuid(),
                mediaQueue.getEntityUuid(),
                mediaQueue.getEntityName(),
                mediaQueue.getFileName(),
                mediaQueue.getType(),
                mediaQueue.getConceptUuid(),
                mediaQueue.getEntityTargetField(),
                mediaQueue.getCreatedDateTime() != null ? Timestamp.valueOf(mediaQueue.getCreatedDateTime()) : null,
                mediaQueue.isUploaded()
            );
            
            logger.debug("Successfully saved MediaQueue: {}", mediaQueue.getUuid());
            
        } catch (DataAccessException e) {
            logger.error("Error saving MediaQueue: {}", mediaQueue.getUuid(), e);
            throw new RuntimeException("Failed to save MediaQueue: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<MediaQueue> findAll() {
        logger.debug("Finding all MediaQueue items");
        
        try {
            String sql = "SELECT * FROM media_queue ORDER BY created_date_time";
            List<MediaQueue> results = jdbcTemplate.query(sql, new MediaQueueRowMapper());
            
            logger.debug("Found {} MediaQueue items", results.size());
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding all MediaQueue items", e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<MediaQueue> findByEntityUuid(String entityUuid) {
        if (entityUuid == null) {
            return new ArrayList<>();
        }
        
        logger.debug("Finding MediaQueue items by entityUuid: {}", entityUuid);
        
        try {
            String sql = "SELECT * FROM media_queue WHERE entity_uuid = ? ORDER BY created_date_time";
            List<MediaQueue> results = jdbcTemplate.query(sql, new MediaQueueRowMapper(), entityUuid);
            
            logger.debug("Found {} MediaQueue items for entityUuid: {}", results.size(), entityUuid);
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding MediaQueue items by entityUuid: {}", entityUuid, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<MediaQueue> findByEntityName(String entityName) {
        if (entityName == null) {
            return new ArrayList<>();
        }
        
        logger.debug("Finding MediaQueue items by entityName: {}", entityName);
        
        try {
            String sql = "SELECT * FROM media_queue WHERE entity_name = ? ORDER BY created_date_time";
            List<MediaQueue> results = jdbcTemplate.query(sql, new MediaQueueRowMapper(), entityName);
            
            logger.debug("Found {} MediaQueue items for entityName: {}", results.size(), entityName);
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding MediaQueue items by entityName: {}", entityName, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public List<MediaQueue> findByType(String type) {
        if (type == null) {
            return new ArrayList<>();
        }
        
        logger.debug("Finding MediaQueue items by type: {}", type);
        
        try {
            String sql = "SELECT * FROM media_queue WHERE type = ? ORDER BY created_date_time";
            List<MediaQueue> results = jdbcTemplate.query(sql, new MediaQueueRowMapper(), type);
            
            logger.debug("Found {} MediaQueue items for type: {}", results.size(), type);
            return results;
            
        } catch (DataAccessException e) {
            logger.error("Error finding MediaQueue items by type: {}", type, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public MediaQueue findByFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        
        logger.debug("Finding MediaQueue by fileName: {}", fileName);
        
        try {
            String sql = "SELECT * FROM media_queue WHERE file_name = ?";
            
            return jdbcTemplate.queryForObject(sql, new MediaQueueRowMapper(), fileName);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No MediaQueue found for fileName: {}", fileName);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding MediaQueue by fileName: {}", fileName, e);
            return null;
        }
    }
    
    @Override
    public MediaQueue findByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        
        logger.debug("Finding MediaQueue by uuid: {}", uuid);
        
        try {
            String sql = "SELECT * FROM media_queue WHERE uuid = ?";
            
            return jdbcTemplate.queryForObject(sql, new MediaQueueRowMapper(), uuid);
            
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No MediaQueue found for uuid: {}", uuid);
            return null;
        } catch (DataAccessException e) {
            logger.error("Error finding MediaQueue by uuid: {}", uuid, e);
            return null;
        }
    }
    
    @Override
    public void delete(MediaQueue mediaQueue) {
        if (mediaQueue == null || mediaQueue.getUuid() == null) {
            logger.warn("Attempted to delete null MediaQueue or MediaQueue with null uuid");
            return;
        }
        
        logger.debug("Deleting MediaQueue: {}", mediaQueue.getUuid());
        
        try {
            String sql = "DELETE FROM media_queue WHERE uuid = ?";
            int deleted = jdbcTemplate.update(sql, mediaQueue.getUuid());
            
            if (deleted > 0) {
                logger.debug("Successfully deleted MediaQueue: {}", mediaQueue.getUuid());
            } else {
                logger.warn("No MediaQueue found with uuid: {} for deletion", mediaQueue.getUuid());
            }
        } catch (DataAccessException e) {
            logger.error("Error deleting MediaQueue: {}", mediaQueue.getUuid(), e);
            throw new RuntimeException("Failed to delete MediaQueue: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteByUuid(String uuid) {
        if (uuid == null) {
            logger.warn("Attempted to delete MediaQueue with null uuid");
            return;
        }
        
        logger.debug("Deleting MediaQueue by uuid: {}", uuid);
        
        try {
            String sql = "DELETE FROM media_queue WHERE uuid = ?";
            int deleted = jdbcTemplate.update(sql, uuid);
            logger.debug("Deleted {} MediaQueue items for uuid: {}", deleted, uuid);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting MediaQueue by uuid: {}", uuid, e);
            throw new RuntimeException("Failed to delete MediaQueue by uuid: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteByEntityUuid(String entityUuid) {
        if (entityUuid == null) {
            logger.warn("Attempted to delete MediaQueue with null entityUuid");
            return;
        }
        
        logger.debug("Deleting MediaQueue by entityUuid: {}", entityUuid);
        
        try {
            String sql = "DELETE FROM media_queue WHERE entity_uuid = ?";
            int deleted = jdbcTemplate.update(sql, entityUuid);
            logger.debug("Deleted {} MediaQueue items for entityUuid: {}", deleted, entityUuid);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting MediaQueue by entityUuid: {}", entityUuid, e);
            throw new RuntimeException("Failed to delete MediaQueue by entityUuid: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteByEntityName(String entityName) {
        if (entityName == null) {
            logger.warn("Attempted to delete MediaQueue with null entityName");
            return;
        }
        
        logger.debug("Deleting MediaQueue by entityName: {}", entityName);
        
        try {
            String sql = "DELETE FROM media_queue WHERE entity_name = ?";
            int deleted = jdbcTemplate.update(sql, entityName);
            logger.debug("Deleted {} MediaQueue items for entityName: {}", deleted, entityName);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting MediaQueue by entityName: {}", entityName, e);
            throw new RuntimeException("Failed to delete MediaQueue by entityName: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteAll() {
        logger.debug("Deleting all MediaQueue items");
        
        try {
            String sql = "DELETE FROM media_queue";
            int deleted = jdbcTemplate.update(sql);
            logger.debug("Deleted {} MediaQueue items", deleted);
            
        } catch (DataAccessException e) {
            logger.error("Error deleting all MediaQueue items", e);
            throw new RuntimeException("Failed to delete all MediaQueue items: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean existsByFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM media_queue WHERE file_name = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fileName);
            return count != null && count > 0;
            
        } catch (DataAccessException e) {
            logger.error("Error checking existence by fileName: {}", fileName, e);
            return false;
        }
    }
    
    @Override
    public boolean existsByEntityUuid(String entityUuid) {
        if (entityUuid == null) {
            return false;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM media_queue WHERE entity_uuid = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, entityUuid);
            return count != null && count > 0;
            
        } catch (DataAccessException e) {
            logger.error("Error checking existence by entityUuid: {}", entityUuid, e);
            return false;
        }
    }
    
    @Override
    public int countByType(String type) {
        if (type == null) {
            return 0;
        }
        
        try {
            String sql = "SELECT COUNT(*) FROM media_queue WHERE type = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, type);
            return count != null ? count : 0;
            
        } catch (DataAccessException e) {
            logger.error("Error counting MediaQueue by type: {}", type, e);
            return 0;
        }
    }
    
    @Override
    public int countTotal() {
        try {
            String sql = "SELECT COUNT(*) FROM media_queue";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
            
        } catch (DataAccessException e) {
            logger.error("Error counting total MediaQueue items", e);
            return 0;
        }
    }
    
    @Override
    public List<String> findDistinctTypes() {
        logger.debug("Finding distinct media types");
        
        try {
            String sql = "SELECT DISTINCT type FROM media_queue ORDER BY type";
            List<String> types = jdbcTemplate.queryForList(sql, String.class);
            
            logger.debug("Found {} distinct media types", types.size());
            return types;
            
        } catch (DataAccessException e) {
            logger.error("Error finding distinct media types", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * RowMapper for converting ResultSet rows to MediaQueue objects
     */
    private static class MediaQueueRowMapper implements RowMapper<MediaQueue> {
        
        @Override
        public MediaQueue mapRow(ResultSet rs, int rowNum) throws SQLException {
            MediaQueue mediaQueue = new MediaQueue();
            
            mediaQueue.setUuid(rs.getString("uuid"));
            mediaQueue.setEntityUuid(rs.getString("entity_uuid"));
            mediaQueue.setEntityName(rs.getString("entity_name"));
            mediaQueue.setFileName(rs.getString("file_name"));
            mediaQueue.setType(rs.getString("type"));
            mediaQueue.setConceptUuid(rs.getString("concept_uuid"));
            mediaQueue.setEntityTargetField(rs.getString("entity_target_field"));
            
            Timestamp createdDateTime = rs.getTimestamp("created_date_time");
            if (createdDateTime != null) {
                mediaQueue.setCreatedDateTime(createdDateTime.toLocalDateTime());
            }
            
            mediaQueue.setUploaded(rs.getBoolean("uploaded"));
            
            return mediaQueue;
        }
    }
}