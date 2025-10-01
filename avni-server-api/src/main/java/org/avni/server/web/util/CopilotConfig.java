package org.avni.server.web.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CopilotConfig {
    
    @JsonProperty("avni_copilot_token")
    private String avniCopilotToken;
    
    @JsonProperty("avni_copilot_enabled")
    private Boolean avniCopilotEnabled;

    @JsonProperty("base_url")
    private String baseUrl;

    public CopilotConfig() {
    }

    public CopilotConfig(String avniCopilotToken, Boolean avniCopilotEnabled, String baseUrl) {
        this.avniCopilotToken = avniCopilotToken;
        this.avniCopilotEnabled = avniCopilotEnabled;
        this.baseUrl = baseUrl;
    }

    public String getAvniCopilotToken() {
        return avniCopilotToken;
    }

    public void setAvniCopilotToken(String avniCopilotToken) {
        this.avniCopilotToken = avniCopilotToken;
    }

    public Boolean getAvniCopilotEnabled() {
        return avniCopilotEnabled;
    }

    public void setAvniCopilotEnabled(Boolean avniCopilotEnabled) {
        this.avniCopilotEnabled = avniCopilotEnabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}