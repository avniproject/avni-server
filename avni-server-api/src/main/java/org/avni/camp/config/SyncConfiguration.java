package org.avni.camp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.avni.camp.client.AvniApiClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration class for Avni Sync components.
 * Handles creation and wiring of all sync-related services and clients.
 */
public class SyncConfiguration {
    
    private final Config config;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final AvniApiClient apiClient;
    
    public SyncConfiguration() {
        this(ConfigFactory.load());
    }
    
    public SyncConfiguration(Config config) {
        this.config = config;
        this.objectMapper = createObjectMapper();
        this.httpClient = createHttpClient();
        this.apiClient = createApiClient();
    }
    
    /**
     * Creates and configures Jackson ObjectMapper for JSON processing
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Creates and configures OkHttpClient for HTTP communication
     */
    private OkHttpClient createHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(getWriteTimeoutSeconds(), TimeUnit.SECONDS);
        
        // Add logging interceptor for debugging
        if (isHttpLoggingEnabled()) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);
        }
        
        // Add authentication interceptor if API key is configured
        String apiKey = getApiKey();
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.addInterceptor(chain -> {
                return chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .build()
                );
            });
        }
        
        return builder.build();
    }
    
    /**
     * Creates and configures Retrofit API client
     */
    private AvniApiClient createApiClient() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getServerUrl())
                .client(httpClient)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();
        
        return retrofit.create(AvniApiClient.class);
    }
    
    // Configuration getters
    public String getServerUrl() {
        return config.getString("avni.server.url");
    }
    
    public String getApiKey() {
        return config.hasPath("avni.server.apiKey") ? config.getString("avni.server.apiKey") : null;
    }
    
    public String getDatabaseUrl() {
        return config.hasPath("avni.database.url") ? 
            config.getString("avni.database.url") : "jdbc:h2:./avni-sync-db";
    }
    
    public String getDatabaseUsername() {
        return config.hasPath("avni.database.username") ? 
            config.getString("avni.database.username") : "sa";
    }
    
    public String getDatabasePassword() {
        return config.hasPath("avni.database.password") ? 
            config.getString("avni.database.password") : "";
    }
    
    public String getMediaBaseDirectory() {
        return config.hasPath("avni.media.baseDirectory") ? 
            config.getString("avni.media.baseDirectory") : "./media";
    }
    
    public String getDeviceId() {
        return config.hasPath("avni.device.id") ? 
            config.getString("avni.device.id") : generateDeviceId();
    }
    
    public int getConnectionTimeoutSeconds() {
        return config.hasPath("avni.http.connectionTimeoutSeconds") ? 
            config.getInt("avni.http.connectionTimeoutSeconds") : 30;
    }
    
    public int getReadTimeoutSeconds() {
        return config.hasPath("avni.http.readTimeoutSeconds") ? 
            config.getInt("avni.http.readTimeoutSeconds") : 60;
    }
    
    public int getWriteTimeoutSeconds() {
        return config.hasPath("avni.http.writeTimeoutSeconds") ? 
            config.getInt("avni.http.writeTimeoutSeconds") : 120;
    }
    
    public boolean isHttpLoggingEnabled() {
        return config.hasPath("avni.http.loggingEnabled") ? 
            config.getBoolean("avni.http.loggingEnabled") : false;
    }
    
    public int getDefaultPageSize() {
        return config.hasPath("avni.sync.pageSize") ? 
            config.getInt("avni.sync.pageSize") : 100;
    }
    
    public Duration getBackgroundSyncInterval() {
        long minutes = config.hasPath("avni.sync.backgroundIntervalMinutes") ? 
            config.getLong("avni.sync.backgroundIntervalMinutes") : 30;
        return Duration.ofMinutes(minutes);
    }
    
    public boolean isAutoSyncEnabled() {
        return config.hasPath("avni.sync.autoSyncEnabled") ? 
            config.getBoolean("avni.sync.autoSyncEnabled") : true;
    }
    
    public int getMaxRetryAttempts() {
        return config.hasPath("avni.sync.maxRetryAttempts") ? 
            config.getInt("avni.sync.maxRetryAttempts") : 3;
    }
    
    public Duration getRetryDelay() {
        long seconds = config.hasPath("avni.sync.retryDelaySeconds") ? 
            config.getLong("avni.sync.retryDelaySeconds") : 5;
        return Duration.ofSeconds(seconds);
    }
    
    // Getters for configured instances
    public Config getConfig() {
        return config;
    }
    
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    public OkHttpClient getHttpClient() {
        return httpClient;
    }
    
    public AvniApiClient getApiClient() {
        return apiClient;
    }
    
    /**
     * Generates a unique device ID if not configured
     */
    private String generateDeviceId() {
        return "java-sync-" + System.currentTimeMillis();
    }
    
    /**
     * Validates the configuration and throws exception if invalid
     */
    public void validate() {
        if (getServerUrl() == null || getServerUrl().trim().isEmpty()) {
            throw new IllegalStateException("Server URL must be configured");
        }
        
        if (!getServerUrl().startsWith("http")) {
            throw new IllegalStateException("Server URL must start with http:// or https://");
        }
        
        if (getConnectionTimeoutSeconds() <= 0) {
            throw new IllegalStateException("Connection timeout must be positive");
        }
        
        if (getReadTimeoutSeconds() <= 0) {
            throw new IllegalStateException("Read timeout must be positive");
        }
        
        if (getWriteTimeoutSeconds() <= 0) {
            throw new IllegalStateException("Write timeout must be positive");
        }
        
        if (getDefaultPageSize() <= 0) {
            throw new IllegalStateException("Page size must be positive");
        }
        
        if (getMaxRetryAttempts() < 0) {
            throw new IllegalStateException("Max retry attempts cannot be negative");
        }
    }
}