package org.avni.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;


@Configuration
@EnableWebMvc
public class StaticResourceConfiguration implements WebMvcConfigurer {


    @Value("${static.path}")
    private String staticPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (staticPath != null) {
            registry
                    .addResourceHandler("/assets/**")
                    .addResourceLocations("file:" + staticPath + "assets/")
                    .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));
            registry
                    .addResourceHandler("/**")
                    .addResourceLocations("file:" + staticPath);
        }
    }

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUseTrailingSlashMatch(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/analytics/**/{path:[^\\.]*}")
                .setViewName("forward:/analytics/index.html");
        registry.addViewController("/userReview")
                .setViewName("forward:/userReview/index.html");
        registry.addViewController("/")
                .setViewName("forward:/index.html");
    }

}
