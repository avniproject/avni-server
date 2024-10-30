package org.avni.server.framework.api;

import org.avni.server.framework.hibernate.DummyInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiConfiguration implements WebMvcConfigurer {
    private final ApiResourceInterceptor apiResourceInterceptor;
    private DummyInterceptor dummyInterceptor;

    private final String[] apiPath = new String[]{"/api/**"};

    @Autowired
    public ApiConfiguration(ApiResourceInterceptor apiResourceInterceptor, DummyInterceptor dummyInterceptor) {
        this.apiResourceInterceptor = apiResourceInterceptor;
        this.dummyInterceptor = dummyInterceptor;
    }

    @Bean("mappedApiResourceInterceptor")
    public MappedInterceptor mappedApiResourceInterceptor() {
        return new MappedInterceptor(apiPath, apiResourceInterceptor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dummyInterceptor);
    }
}
