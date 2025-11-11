package org.avni.server.web.util;

public class AvniAiConfig {
    
    private String token;
    
    private Boolean enabled;

    private String baseUrl;

    private String mcpServerUrl;

    private Boolean showTemplates;

    public AvniAiConfig() {
    }

    public AvniAiConfig(String token, Boolean enabled, String baseUrl, String mcpServerUrl, Boolean showTemplates) {
        this.token = token;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.mcpServerUrl = mcpServerUrl;
        this.showTemplates = showTemplates;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public static AvniAiConfig create(String copilotToken, Boolean copilotEnabled, String baseUrl, String mcpServerUrl, Boolean showTemplates) {
        if ("dummy".equals(copilotToken)) {
            return new AvniAiConfig(null, copilotEnabled, baseUrl, mcpServerUrl, showTemplates);
        }
        
        return new AvniAiConfig(copilotToken, copilotEnabled, baseUrl, mcpServerUrl, showTemplates);
    }
}