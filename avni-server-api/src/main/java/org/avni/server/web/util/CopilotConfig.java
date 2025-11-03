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

    @JsonProperty("show_templates")
    private Boolean showTemplates;

    public CopilotConfig() {
    }

    public CopilotConfig(String avniCopilotToken, Boolean avniCopilotEnabled, String baseUrl, String mcpServerUrl, Boolean showTemplates) {
        this.avniCopilotToken = avniCopilotToken;
        this.avniCopilotEnabled = avniCopilotEnabled;
        this.baseUrl = baseUrl;
        this.mcpServerUrl = mcpServerUrl;
        this.showTemplates = showTemplates;
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

    public Boolean getShowTemplates() {
        return showTemplates;
    }

    public void setShowTemplates(Boolean showTemplates) {
        this.showTemplates = showTemplates;
    }
}