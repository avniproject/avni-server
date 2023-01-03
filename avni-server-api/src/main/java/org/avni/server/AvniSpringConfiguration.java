package org.avni.server;

import com.google.common.cache.CacheBuilder;
import org.avni.server.domain.User;
import org.avni.server.framework.jpa.CHSAuditorAware;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
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
import org.springframework.core.env.Environment;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

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

    @Value("${avni.custom.query.timeout}")
    private int timeout;

    @Value("${avni.custom.query.max.rows}")
    private int maxRows;

    @Autowired
    public AvniSpringConfiguration(Environment environment, @Qualifier("dataSource") DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
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

    @Bean(name = "externalQueryJdbcTemplate")
    public NamedParameterJdbcTemplate getExternalQueryJdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(timeout);
        jdbcTemplate.setMaxRows(maxRows);
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Bean
    public KeycloakSpringBootConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager() {
            /**
             * IMPORTANT:
             * 1. We should keep the value for timeToLiveInSeconds to a low value (less than 10 seconds),
             * so that the app recovers from an OOM issue after the entries expire (in the next 10 seconds)
             * 2. We should keep the value for maxEntriesToCache to a low value (less than 5),
             * so that we avoid getting into OOM issue in the first place. Ex: LAHI catchment have 100mb footprint,
             * storing 5 of them at max in 2 different caches would eat up about 1gb of heap space
             */
            @Override
            protected Cache createConcurrentMapCache(final String name) {
                return new ConcurrentMapCache(name, CacheBuilder.newBuilder().expireAfterWrite(timeToLiveInSeconds,
                        TimeUnit.SECONDS).maximumSize(maxEntriesToCache).build().asMap(), DISALLOW_NULL_VALUES);
            }
        };
    }
}
