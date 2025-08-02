package org.avni.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.avni.sync.client.AvniApiClient;
import org.avni.sync.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for Avni Sync components.
 * Creates and wires all sync-related beans.
 */
@Configuration
@EnableConfigurationProperties(SyncConfigurationProperties.class)
public class SyncConfig {
    
    private final SyncConfigurationProperties syncProperties;
    
    @Autowired
    public SyncConfig(SyncConfigurationProperties syncProperties) {
        this.syncProperties = syncProperties;
    }
    
    /**
     * Creates ObjectMapper for JSON serialization/deserialization
     */
    @Bean(name = "syncObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Creates HTTP client for API calls
     */
    @Bean(name = "syncHttpClient")
    public OkHttpClient httpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(syncProperties.getHttp().getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
            .readTimeout(syncProperties.getHttp().getReadTimeoutSeconds(), TimeUnit.SECONDS)
            .writeTimeout(syncProperties.getHttp().getWriteTimeoutSeconds(), TimeUnit.SECONDS);
        
        // Add logging interceptor if enabled
        if (syncProperties.getHttp().isLoggingEnabled()) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);
        }
        
        return builder.build();
    }
    
    /**
     * Creates Retrofit instance for API client
     */
    @Bean(name = "syncRetrofit")
    public Retrofit retrofit(ObjectMapper objectMapper, OkHttpClient httpClient) {
        return new Retrofit.Builder()
            .baseUrl(syncProperties.getServerUrl())
            .client(httpClient)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build();
    }
    
    /**
     * Creates Avni API client
     */
    @Bean
    public AvniApiClient avniApiClient(Retrofit retrofit) {
        return retrofit.create(AvniApiClient.class);
    }
    
    /**
     * Creates Sync Service
     * Note: This is a placeholder implementation. In a real application,
     * you would inject the actual repositories and services needed.
     */
    @Bean
    public SyncService syncService(AvniApiClient apiClient) {
        // TODO: Create actual SyncService implementation with proper dependencies
        // For now, returning null to indicate service not fully implemented
        return null;
    }
}
