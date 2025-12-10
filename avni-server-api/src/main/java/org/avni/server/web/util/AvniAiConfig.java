package org.avni.server.web.util;

public class AvniAiConfig {

    private String token;

    private Boolean enabled;

    private String baseUrl;

    private String mcpServerUrl;

    private Boolean showTemplates;

    private String copilotFormValidationApiKey;

    public AvniAiConfig() {
    }

    public AvniAiConfig(String token, Boolean enabled, String baseUrl, String mcpServerUrl, Boolean showTemplates, String copilotFormValidationApiKey) {
        this.token = token;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.mcpServerUrl = mcpServerUrl;
        this.showTemplates = showTemplates;
        this.copilotFormValidationApiKey = copilotFormValidationApiKey;
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

    public static AvniAiConfig create(String copilotToken, Boolean copilotEnabled, String baseUrl, String mcpServerUrl, Boolean showTemplates, String copilotFormValidationApiKey) {
        String token = copilotToken != null && !copilotToken.isEmpty() ? copilotToken : null;
        String formValidationApiKey = (copilotFormValidationApiKey != null && !copilotFormValidationApiKey.isEmpty()) ? copilotFormValidationApiKey : null;
        return new AvniAiConfig(token, copilotEnabled, baseUrl, mcpServerUrl, showTemplates, formValidationApiKey);
    }

    public String getCopilotFormValidationApiKey() {
        return copilotFormValidationApiKey;
    }

    public void setCopilotFormValidationApiKey(String copilotFormValidationApiKey) {
        this.copilotFormValidationApiKey = copilotFormValidationApiKey;
    }

}
