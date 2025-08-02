package org.avni.camp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.avni.camp.client.AvniApiClient;
import org.avni.camp.web.AuthController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for Avni Sync components.
 * Creates and wires all sync-related beans.
 */
@Configuration
@EnableConfigurationProperties(CampConfigurationProperties.class)
public class CampConfig {
    
    private final CampConfigurationProperties campProperties;
    
    @Autowired
    public CampConfig(CampConfigurationProperties campProperties) {
        this.campProperties = campProperties;
    }
    
    /**
     * Creates ObjectMapper for JSON serialization/deserialization
     */
    @Bean(name = "campObjectMapper")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Creates HTTP client for API calls
     */
    @Bean(name = "campHttpClient")
    public OkHttpClient httpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(campProperties.getHttp().getConnectionTimeoutSeconds(), TimeUnit.SECONDS)
            .readTimeout(campProperties.getHttp().getReadTimeoutSeconds(), TimeUnit.SECONDS)
            .writeTimeout(campProperties.getHttp().getWriteTimeoutSeconds(), TimeUnit.SECONDS);
        
        // Add auth interceptor to automatically include auth token
        builder.addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                String authToken = AuthController.getStoredAuthToken();
                
                if (authToken != null) {
                    Request authenticatedRequest = originalRequest.newBuilder()
                        .header("auth-token", authToken)
                        .build();
                    return chain.proceed(authenticatedRequest);
                }
                
                return chain.proceed(originalRequest);
            }
        });
        
        // Add logging interceptor if enabled
        if (campProperties.getHttp().isLoggingEnabled()) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(loggingInterceptor);
        }
        
        return builder.build();
    }
    
    /**
     * Creates Retrofit instance for API client
     */
    @Bean(name = "campRetrofit")
    public Retrofit retrofit(@Qualifier("campObjectMapper") ObjectMapper objectMapper, @Qualifier("campHttpClient") OkHttpClient httpClient) {
        return new Retrofit.Builder()
            .baseUrl(campProperties.getServerUrl())
            .client(httpClient)
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build();
    }
    
    /**
     * Creates AvniApiClient instance
     */
    @Bean
    public AvniApiClient avniApiClient(@Qualifier("campRetrofit") Retrofit retrofit) {
        return retrofit.create(AvniApiClient.class);
    }
    
}
