package org.avni.camp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.camp.client.AvniApiClient;
import org.avni.camp.persistence.*;
import org.avni.camp.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import retrofit2.Retrofit;

/**
 * Spring Boot configuration for PostgreSQL-based sync implementation.
 * Wires up all the necessary services, repositories, and clients.
 */
@Configuration
@EnableConfigurationProperties(CampConfigurationProperties.class)
public class PostgreSQLCampConfiguration {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    @Qualifier("campObjectMapper")
    private ObjectMapper objectMapper;
    
    @Autowired
    private CampConfigurationProperties configProperties;
    
    /**
     * Creates PostgreSQL-based EntityPersistenceService bean
     */
    @Bean
    public EntityPersistenceService entityPersistenceService() {
        PostgreSQLEntityPersistenceService service = new PostgreSQLEntityPersistenceService(jdbcTemplate, objectMapper);
        
        // Initialize sync schema on startup
        service.initializeSchema();
        
        return service;
    }
    
    /**
     * Creates PostgreSQL-based EntitySyncStatusRepository bean
     */
    @Bean
    public EntitySyncStatusRepository entitySyncStatusRepository() {
        return new PostgreSQLEntitySyncStatusRepository(jdbcTemplate);
    }
    
    /**
     * Creates PostgreSQL-based SyncTelemetryRepository bean
     */
    @Bean(name = "postgreSQLSyncTelemetryRepository")
    public org.avni.camp.persistence.SyncTelemetryRepository syncTelemetryRepository() {
        return new PostgreSQLSyncTelemetryRepository(jdbcTemplate, objectMapper);
    }
    
    /**
     * Creates PostgreSQL-based EntityQueueRepository bean
     */
    @Bean
    public EntityQueueRepository entityQueueRepository() {
        return new PostgreSQLEntityQueueRepository(jdbcTemplate);
    }
    
    /**
     * Creates PostgreSQL-based MediaQueueRepository bean
     */
    @Bean
    public MediaQueueRepository mediaQueueRepository() {
        return new PostgreSQLMediaQueueRepository(jdbcTemplate);
    }
    
    /**
     * Creates EntityQueueService bean
     */
    @Bean
    public EntityQueueService entityQueueService(EntityQueueRepository entityQueueRepository) {
        return new EntityQueueService(entityQueueRepository);
    }
    
    /**
     * Creates MediaQueueService bean
     */
    @Bean
    public MediaQueueService mediaQueueService(
            MediaQueueRepository mediaQueueRepository,
            AvniApiClient apiClient,
            EntityPersistenceService entityPersistenceService) {
        return new MediaQueueService(
            mediaQueueRepository, 
            apiClient, 
            entityPersistenceService, 
            configProperties.getMediaBaseDirectory()
        );
    }
    
    /**
     * Creates EntityMetadataService bean
     */
    @Bean
    public EntityMetadataService entityMetadataService() {
        return new EntityMetadataService();
    }
    
    /**
     * Creates the main SyncService bean
     */
    @Bean
    public SyncService syncService(
            AvniApiClient apiClient,
            EntitySyncStatusRepository syncStatusRepository,
            @Qualifier("postgreSQLSyncTelemetryRepository") org.avni.camp.persistence.SyncTelemetryRepository telemetryRepository,
            EntityQueueService entityQueueService,
            MediaQueueService mediaQueueService,
            EntityPersistenceService entityPersistenceService) {
        
        return new SyncService(
            apiClient,
            syncStatusRepository,
            telemetryRepository,
            entityQueueService,
            mediaQueueService,
            entityPersistenceService,
            configProperties.getDeviceId()
        );
    }
}