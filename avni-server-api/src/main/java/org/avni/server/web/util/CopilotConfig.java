package org.avni.server.web.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CopilotConfig {
    
    @JsonProperty("avni_copilot_token")
    private String avniCopilotToken;
    
    @JsonProperty("avni_copilot_enabled")
    private Boolean avniCopilotEnabled;

    public CopilotConfig() {
    }

    public CopilotConfig(String avniCopilotToken, Boolean avniCopilotEnabled) {
        this.avniCopilotToken = avniCopilotToken;
        this.avniCopilotEnabled = avniCopilotEnabled;
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
}