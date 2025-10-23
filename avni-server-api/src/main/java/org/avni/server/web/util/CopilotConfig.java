package org.avni.server.web.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CopilotConfig {
    
    @JsonProperty("avni_copilot_token")
    private String avniCopilotToken;
    
    @JsonProperty("avni_copilot_enabled")
    private Boolean avniCopilotEnabled;

    @JsonProperty("base_url")
    private String baseUrl;

    @JsonProperty("mcp_server_url")
    private String mcpServerUrl;

    public CopilotConfig() {
    }

    public CopilotConfig(String avniCopilotToken, Boolean avniCopilotEnabled, String baseUrl, String mcpServerUrl) {
        this.avniCopilotToken = avniCopilotToken;
        this.avniCopilotEnabled = avniCopilotEnabled;
        this.baseUrl = baseUrl;
        this.mcpServerUrl = mcpServerUrl;
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

    public String getMcpServerUrl() {
        return mcpServerUrl;
    }

    public void setMcpServerUrl(String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
    }
}