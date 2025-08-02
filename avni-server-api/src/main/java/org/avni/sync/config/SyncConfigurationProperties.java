package org.avni.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Spring Boot configuration properties for Avni Sync.
 * Maps to the sync configuration properties in application.properties.
 */
@Component
@ConfigurationProperties(prefix = "avni.sync")
public class SyncConfigurationProperties {
    
    private Server server = new Server();
    private Database database = new Database();
    private Media media = new Media();
    private Device device = new Device();
    private Http http = new Http();
    private boolean autoSyncEnabled = true;
    private int backgroundIntervalMinutes = 30;
    private int pageSize = 100;
    private int maxRetryAttempts = 3;
    private int retryDelaySeconds = 5;
    
    // Getters and setters
    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }
    
    public Database getDatabase() { return database; }
    public void setDatabase(Database database) { this.database = database; }
    
    public Media getMedia() { return media; }
    public void setMedia(Media media) { this.media = media; }
    
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    
    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }
    
    public boolean isAutoSyncEnabled() { return autoSyncEnabled; }
    public void setAutoSyncEnabled(boolean autoSyncEnabled) { this.autoSyncEnabled = autoSyncEnabled; }
    
    public int getBackgroundIntervalMinutes() { return backgroundIntervalMinutes; }
    public void setBackgroundIntervalMinutes(int backgroundIntervalMinutes) { this.backgroundIntervalMinutes = backgroundIntervalMinutes; }
    
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    
    public int getRetryDelaySeconds() { return retryDelaySeconds; }
    public void setRetryDelaySeconds(int retryDelaySeconds) { this.retryDelaySeconds = retryDelaySeconds; }
    
    // Convenience methods for compatibility with original SyncConfiguration
    public String getServerUrl() { return server.getUrl(); }
    public String getApiKey() { return server.getApiKey(); }
    public String getDatabaseUrl() { return database.getUrl(); }
    public String getDatabaseUsername() { return database.getUsername(); }
    public String getDatabasePassword() { return database.getPassword(); }
    public String getMediaBaseDirectory() { return media.getBaseDirectory(); }
    public String getDeviceId() { return device.getId(); }
    public Duration getBackgroundSyncInterval() { return Duration.ofMinutes(backgroundIntervalMinutes); }
    public int getDefaultPageSize() { return pageSize; }
    
    /**
     * Validates the configuration
     */
    public void validate() {
        if (server.getUrl() == null || server.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Server URL is required");
        }
        if (database.getUrl() == null || database.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Database URL is required");
        }
        if (media.getBaseDirectory() == null || media.getBaseDirectory().trim().isEmpty()) {
            throw new IllegalArgumentException("Media base directory is required");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        if (maxRetryAttempts < 0) {
            throw new IllegalArgumentException("Max retry attempts cannot be negative");
        }
        if (retryDelaySeconds < 0) {
            throw new IllegalArgumentException("Retry delay cannot be negative");
        }
    }
    
    public static class Server {
        private String url = "https://your-avni-server.com";
        private String apiKey;
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
    
    public static class Database {
        private String url = "jdbc:h2:./data/avni-sync-db";
        private String username = "sa";
        private String password = "";
        
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class Media {
        private String baseDirectory = "./media";
        
        public String getBaseDirectory() { return baseDirectory; }
        public void setBaseDirectory(String baseDirectory) { this.baseDirectory = baseDirectory; }
    }
    
    public static class Device {
        private String id;
        
        public String getId() { 
            if (id == null || id.trim().isEmpty()) {
                // Generate a device ID if not provided
                id = "device-" + System.currentTimeMillis();
            }
            return id; 
        }
        public void setId(String id) { this.id = id; }
    }
    
    public static class Http {
        private boolean loggingEnabled = false;
        private int connectionTimeoutSeconds = 30;
        private int readTimeoutSeconds = 60;
        private int writeTimeoutSeconds = 120;
        
        public boolean isLoggingEnabled() { return loggingEnabled; }
        public void setLoggingEnabled(boolean loggingEnabled) { this.loggingEnabled = loggingEnabled; }
        public int getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) { this.connectionTimeoutSeconds = connectionTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
        public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
        public void setWriteTimeoutSeconds(int writeTimeoutSeconds) { this.writeTimeoutSeconds = writeTimeoutSeconds; }
    }
}
