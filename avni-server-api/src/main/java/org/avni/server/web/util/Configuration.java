package org.avni.server.web.util;

import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "avni")
public class Configuration {
    private List<ReportingSystem> reportingSystems;

    public List<ReportingSystem> getReportingSystems() {
        return reportingSystems;
    }

    public void setReportingSystems(List<ReportingSystem> reportingSystems) {
        this.reportingSystems = reportingSystems;
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        MappingJackson2HttpMessageConverter jsonMessageConverter = new MappingJackson2HttpMessageConverter();
        jsonMessageConverter.setObjectMapper(ObjectMapperSingleton.getObjectMapper());
        messageConverters.add(jsonMessageConverter);
        restTemplate.setMessageConverters(messageConverters);
        return restTemplate;
    }
}
