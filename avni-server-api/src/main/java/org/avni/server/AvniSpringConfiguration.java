package org.avni.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.deser.DateTimeDeserializer;
import com.google.common.cache.CacheBuilder;
import org.avni.server.application.projections.CatchmentAddressProjection;
import org.avni.server.domain.User;
import org.avni.server.framework.jpa.CHSAuditorAware;
import org.avni.server.util.ObjectMapperSingleton;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT;
import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS;

@Configuration
@EnableCaching
@EnableJpaAuditing
@EnableWebMvc
public class AvniSpringConfiguration extends WebMvcAutoConfiguration {

    public static final boolean DISALLOW_NULL_VALUES = false;

    private final Environment environment;
    private final DataSource dataSource;

    @Value("${avni.cache.max.entries}")
    private int maxEntriesToCache;

    @Value("${avni.cache.ttl.seconds}")
    private int timeToLiveInSeconds;

    @Value("${avni.cache.max.weight}")
    private int cacheMaxWeight;

    @Value("${avni.custom.query.timeout}")
    private int timeout;

    @Value("${avni.custom.query.max.rows}")
    private int maxRows;

    @Value("${avni.keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String keycloakResource;

    @Value("${keycloak.auth-server-url}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.ssl-required}")
    private String sslRequired;

    @Value("${keycloak.credentials.secret}")
    private String keycloakCredentialsSecret;

    @Value("${keycloak.use-resource-role-mappings}")
    private boolean useResourceRoleMappings;

    @Autowired
    public AvniSpringConfiguration(Environment environment, @Qualifier("dataSource") DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    // https://stackoverflow.com/questions/62571413/spring-keycloak-adapter-loads-open-id-configuration-for-every-single-request
    @Bean
    public AdapterConfig adapterConfig() {
        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setRealm(realm);
        adapterConfig.setResource(keycloakResource);
        adapterConfig.setAuthServerUrl(keycloakAuthServerUrl);
        adapterConfig.setSslRequired(sslRequired);

        HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("secret", keycloakCredentialsSecret);
        adapterConfig.setCredentials(credentials);
        adapterConfig.setUseResourceRoleMappings(useResourceRoleMappings);
        return adapterConfig;
    }

    @Bean
    public AuditorAware<User> auditorProvider() {
        return new CHSAuditorAware();
    }

    @Bean
    public SpelAwareProxyProjectionFactory projectionFactory() {
        return new SpelAwareProxyProjectionFactory();
    }

    @Bean
    public Boolean isDev() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 1 && (activeProfiles[0].equals("dev") || activeProfiles[0].equals("test"));
    }

    @Bean
    @Primary
    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Bean(name = "externalQueryJdbcTemplate")
    public NamedParameterJdbcTemplate getExternalQueryJdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(timeout);
        jdbcTemplate.setMaxRows(maxRows);
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Bean
    public KeycloakDeployment keycloakDeployment(AdapterConfig adapterConfig) {
        return KeycloakDeploymentBuilder.build(adapterConfig);
    }

    @Bean
    public KeycloakConfigResolver keycloakConfigResolver(KeycloakDeployment keycloakDeployment) {
        return request -> keycloakDeployment;
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager() {
            @Override
            protected Cache createConcurrentMapCache(final String name) {
                switch (name) {
                    case ADDRESSES_PER_CATCHMENT:
                    case ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS:
                        return getConcurrentMapCacheWithWeightedCapacityForAddressesConfig(name);
                    default:
                        return getConcurrentMapCacheWithMaxEntriesConfig(name);
                }
            }

            private ConcurrentMapCache getConcurrentMapCacheWithWeightedCapacityForAddressesConfig(String name) {
                return new ConcurrentMapCache(name, CacheBuilder.newBuilder().expireAfterWrite(timeToLiveInSeconds,
                                TimeUnit.SECONDS).maximumWeight(cacheMaxWeight)
                        .weigher((key, value) -> value == null ? 0 : (((List<CatchmentAddressProjection>) value).size() / 100) + 1)
                        .weakKeys()
                        .softValues()
                        .build().asMap(), DISALLOW_NULL_VALUES);
            }

            private ConcurrentMapCache getConcurrentMapCacheWithMaxEntriesConfig(String name) {
                return new ConcurrentMapCache(name, CacheBuilder.newBuilder()
                                .expireAfterWrite(timeToLiveInSeconds, TimeUnit.SECONDS)
                                .maximumSize(maxEntriesToCache)
                        .build().asMap(), DISALLOW_NULL_VALUES);
            }
        };
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
        return objectMapper;
    }
}
