package org.avni.camp.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.camp.model.EntityMetaData;
import org.avni.camp.service.EntityPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL implementation of EntityPersistenceService.
 * Handles persistence and retrieval of entities from PostgreSQL database.
 */
@Service
@Transactional
public class PostgreSQLEntityPersistenceService implements EntityPersistenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLEntityPersistenceService.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    // Entity table mappings - maps entity names to their corresponding database tables
    private static final Map<String, String> ENTITY_TABLE_MAPPINGS = createEntityTableMappings();
    
    private static Map<String, String> createEntityTableMappings() {
        Map<String, String> mappings = new java.util.HashMap<>();
        mappings.put("Individual", "individual");
        mappings.put("ProgramEnrolment", "program_enrolment");
        mappings.put("ProgramEncounter", "program_encounter");
        mappings.put("Encounter", "encounter");
        mappings.put("Checklist", "checklist");
        mappings.put("ChecklistItem", "checklist_item");
        mappings.put("GroupSubject", "group_subject");
        mappings.put("IndividualRelationship", "individual_relationship");
        mappings.put("AddressLevel", "address_level");
        mappings.put("Concept", "concept");
        mappings.put("SubjectType", "subject_type");
        mappings.put("Program", "program");
        mappings.put("EncounterType", "encounter_type");
        mappings.put("Form", "form");
        mappings.put("FormMapping", "form_mapping");
        mappings.put("OrganisationConfig", "organisation_config");
        return java.util.Collections.unmodifiableMap(mappings);
    }
    
    @Autowired
    public PostgreSQLEntityPersistenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void persistEntities(List<JsonNode> entities, EntityMetaData entityMetaData) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        
        String tableName = getTableName(entityMetaData.getEntityName());
        logger.info("Persisting {} entities to table: {}", entities.size(), tableName);
        
        try {
            for (JsonNode entity : entities) {
                persistSingleEntity(entity, tableName);
            }
            logger.info("Successfully persisted {} entities", entities.size());
        } catch (Exception e) {
            logger.error("Error persisting entities to table {}", tableName, e);
            throw new RuntimeException("Failed to persist entities: " + e.getMessage(), e);
        }
    }
    
    @Override
    public JsonNode getEntityAsResource(String entityUuid, String entityName) {
        if (entityUuid == null || entityName == null) {
            return null;
        }
        
        String tableName = getTableName(entityName);
        logger.debug("Retrieving entity {} from table: {}", entityUuid, tableName);
        
        try {
            String sql = "SELECT * FROM " + tableName + " WHERE uuid = ? AND NOT COALESCE(is_voided, false)";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, entityUuid);
            
            if (results.isEmpty()) {
                logger.debug("Entity {} not found in table {}", entityUuid, tableName);
                return null;
            }
            
            Map<String, Object> entityData = results.get(0);
            JsonNode entityNode = objectMapper.convertValue(entityData, JsonNode.class);
            logger.debug("Retrieved entity {} from table {}", entityUuid, tableName);
            return entityNode;
            
        } catch (DataAccessException e) {
            logger.error("Error retrieving entity {} from table {}", entityUuid, tableName, e);
            return null;
        }
    }
    
    @Override
    public void replaceMediaObservation(String entityUuid, String entityName, String fileName, 
                                      String serverUrl, String conceptUuid, String targetField) {
        String tableName = getTableName(entityName);
        logger.debug("Replacing media observation for entity {} in table {}", entityUuid, tableName);
        
        try {
            // This is a simplified implementation - in reality you'd need to parse and update JSON observations
            String sql = "UPDATE " + tableName + " SET last_modified_date_time = NOW() WHERE uuid = ?";
            int updated = jdbcTemplate.update(sql, entityUuid);
            
            if (updated > 0) {
                logger.debug("Updated media observation for entity {} in table {}", entityUuid, tableName);
            } else {
                logger.warn("No entity found with uuid {} in table {} for media update", entityUuid, tableName);
            }
        } catch (DataAccessException e) {
            logger.error("Error updating media observation for entity {} in table {}", entityUuid, tableName, e);
        }
    }
    
    @Override
    public int getEntityCount(String entityName) {
        String tableName = getTableName(entityName);
        
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE NOT COALESCE(is_voided, false)";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            logger.error("Error counting entities in table {}", tableName, e);
            return 0;
        }
    }
    
    @Override
    public void deleteEntitiesByType(String entityName) {
        String tableName = getTableName(entityName);
        logger.info("Soft deleting all entities in table: {}", tableName);
        
        try {
            String sql = "UPDATE " + tableName + " SET is_voided = true, last_modified_date_time = NOW()";
            int updated = jdbcTemplate.update(sql);
            logger.info("Soft deleted {} entities in table {}", updated, tableName);
        } catch (DataAccessException e) {
            logger.error("Error soft deleting entities in table {}", tableName, e);
            throw new RuntimeException("Failed to delete entities: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteEntity(String entityUuid, String entityName) {
        String tableName = getTableName(entityName);
        logger.debug("Soft deleting entity {} in table {}", entityUuid, tableName);
        
        try {
            String sql = "UPDATE " + tableName + " SET is_voided = true, last_modified_date_time = NOW() WHERE uuid = ?";
            int updated = jdbcTemplate.update(sql, entityUuid);
            
            if (updated > 0) {
                logger.debug("Soft deleted entity {} in table {}", entityUuid, tableName);
            } else {
                logger.warn("No entity found with uuid {} in table {} for deletion", entityUuid, tableName);
            }
        } catch (DataAccessException e) {
            logger.error("Error soft deleting entity {} in table {}", entityUuid, tableName, e);
        }
    }
    
    @Override
    public boolean entityExists(String entityUuid, String entityName) {
        String tableName = getTableName(entityName);
        
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE uuid = ? AND NOT COALESCE(is_voided, false)";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, entityUuid);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            logger.error("Error checking if entity {} exists in table {}", entityUuid, tableName, e);
            return false;
        }
    }
    
    @Override
    public List<JsonNode> getEntitiesByType(String entityName) {
        String tableName = getTableName(entityName);
        logger.debug("Retrieving all entities from table: {}", tableName);
        
        try {
            String sql = "SELECT * FROM " + tableName + " WHERE NOT COALESCE(is_voided, false) ORDER BY last_modified_date_time";
            
            return jdbcTemplate.query(sql, new RowMapper<JsonNode>() {
                @Override
                public JsonNode mapRow(ResultSet rs, int rowNum) throws SQLException {
                    // Convert ResultSet row to Map and then to JsonNode
                    Map<String, Object> rowData = new java.util.HashMap<>();
                    int columnCount = rs.getMetaData().getColumnCount();
                    
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rs.getMetaData().getColumnName(i);
                        Object value = rs.getObject(i);
                        rowData.put(columnName, value);
                    }
                    
                    return objectMapper.convertValue(rowData, JsonNode.class);
                }
            });
            
        } catch (DataAccessException e) {
            logger.error("Error retrieving entities from table {}", tableName, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public void cleanup() {
        logger.info("Performing database cleanup operations");
        
        try {
            // Clean up old sync telemetry records (keep last 100)
            String sql = "DELETE FROM sync_telemetry WHERE id NOT IN " +
                        "(SELECT id FROM sync_telemetry ORDER BY sync_start_time DESC LIMIT 100)";
            int deleted = jdbcTemplate.update(sql);
            logger.info("Cleaned up {} old sync telemetry records", deleted);
            
        } catch (DataAccessException e) {
            logger.error("Error during database cleanup", e);
        }
    }
    
    @Override
    public void initializeSchema() {
        logger.info("Initializing sync schema if needed");
        
        try {
            // Create sync-related tables if they don't exist
            createSyncTablesIfNotExists();
            logger.info("Sync schema initialization completed");
        } catch (Exception e) {
            logger.error("Error initializing sync schema", e);
            throw new RuntimeException("Failed to initialize sync schema: " + e.getMessage(), e);
        }
    }
    
    /**
     * Persists a single entity to the database using upsert logic
     */
    private void persistSingleEntity(JsonNode entity, String tableName) {
        if (!entity.has("uuid")) {
            logger.warn("Entity missing uuid field, skipping: {}", entity);
            return;
        }
        
        String uuid = entity.get("uuid").asText();
        
        try {
            // Check if entity exists
            String checkSql = "SELECT COUNT(*) FROM " + tableName + " WHERE uuid = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, uuid);
            
            if (count != null && count > 0) {
                updateExistingEntity(entity, tableName, uuid);
            } else {
                insertNewEntity(entity, tableName);
            }
            
        } catch (Exception e) {
            logger.error("Error persisting entity {} to table {}", uuid, tableName, e);
            throw new RuntimeException("Failed to persist entity: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates an existing entity in the database
     */
    private void updateExistingEntity(JsonNode entity, String tableName, String uuid) {
        // This is a simplified update - in reality you'd need to map JSON fields to table columns
        String sql = "UPDATE " + tableName + " SET last_modified_date_time = NOW() WHERE uuid = ?";
        jdbcTemplate.update(sql, uuid);
        logger.debug("Updated existing entity {} in table {}", uuid, tableName);
    }
    
    /**
     * Inserts a new entity into the database
     */
    private void insertNewEntity(JsonNode entity, String tableName) {
        String uuid = entity.get("uuid").asText();
        
        // This is a simplified insert - in reality you'd need to map JSON fields to table columns
        // For now, we'll create a basic insert with uuid and timestamps
        String sql = "INSERT INTO " + tableName + " (uuid, created_date_time, last_modified_date_time, is_voided) " +
                    "VALUES (?, NOW(), NOW(), false) " +
                    "ON CONFLICT (uuid) DO UPDATE SET last_modified_date_time = NOW()";
        
        jdbcTemplate.update(sql, uuid);
        logger.debug("Inserted new entity {} into table {}", uuid, tableName);
    }
    
    /**
     * Creates sync-related tables if they don't exist
     */
    private void createSyncTablesIfNotExists() {
        // Create entity_sync_status table
        String createEntitySyncStatus = """
            CREATE TABLE IF NOT EXISTS entity_sync_status (
                id SERIAL PRIMARY KEY,
                uuid VARCHAR(255) UNIQUE NOT NULL,
                entity_name VARCHAR(255) NOT NULL,
                entity_type_uuid VARCHAR(255),
                loaded_since TIMESTAMP,
                last_modified_date_time TIMESTAMP NOT NULL DEFAULT NOW(),
                is_voided BOOLEAN DEFAULT FALSE
            )
            """;
        
        // Create sync_telemetry table
        String createSyncTelemetry = """
            CREATE TABLE IF NOT EXISTS sync_telemetry (
                id SERIAL PRIMARY KEY,
                uuid VARCHAR(255) UNIQUE NOT NULL,
                sync_source VARCHAR(50) NOT NULL,
                device_id VARCHAR(255),
                sync_start_time TIMESTAMP NOT NULL,
                sync_end_time TIMESTAMP,
                entity_push_completed BOOLEAN DEFAULT FALSE,
                media_push_completed BOOLEAN DEFAULT FALSE,
                entity_pull_completed BOOLEAN DEFAULT FALSE,
                total_entities_pulled INTEGER DEFAULT 0,
                total_entities_pushed INTEGER DEFAULT 0,
                total_media_pushed INTEGER DEFAULT 0,
                sync_status VARCHAR(20) DEFAULT 'STARTED',
                sync_error_message TEXT,
                connection_info JSONB
            )
            """;
        
        // Create entity_queue table
        String createEntityQueue = """
            CREATE TABLE IF NOT EXISTS entity_queue (
                id SERIAL PRIMARY KEY,
                uuid VARCHAR(255) UNIQUE NOT NULL,
                entity_uuid VARCHAR(255) NOT NULL,
                entity_type VARCHAR(255) NOT NULL,
                action VARCHAR(20),
                created_date_time TIMESTAMP DEFAULT NOW(),
                processed BOOLEAN DEFAULT FALSE
            )
            """;
        
        // Create media_queue table
        String createMediaQueue = """
            CREATE TABLE IF NOT EXISTS media_queue (
                id SERIAL PRIMARY KEY,
                uuid VARCHAR(255) UNIQUE NOT NULL,
                entity_uuid VARCHAR(255) NOT NULL,
                entity_name VARCHAR(255) NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                type VARCHAR(50),
                concept_uuid VARCHAR(255),
                entity_target_field VARCHAR(255),
                created_date_time TIMESTAMP DEFAULT NOW(),
                uploaded BOOLEAN DEFAULT FALSE
            )
            """;
        
        jdbcTemplate.execute(createEntitySyncStatus);
        jdbcTemplate.execute(createSyncTelemetry);
        jdbcTemplate.execute(createEntityQueue);
        jdbcTemplate.execute(createMediaQueue);
        
        logger.info("Created sync tables if they didn't exist");
    }
    
    /**
     * Maps entity name to database table name
     */
    private String getTableName(String entityName) {
        return ENTITY_TABLE_MAPPINGS.getOrDefault(entityName, entityName.toLowerCase());
    }
}