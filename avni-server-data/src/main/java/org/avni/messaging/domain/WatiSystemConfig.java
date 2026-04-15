package org.avni.messaging.domain;

import org.avni.server.domain.extenalSystem.ExternalSystemConfig;

import java.util.List;
import java.util.Map;

/**
 * Wraps the external_system_config jsonb for system_name = 'Wati'.
 * Unlike Glific (per-org credentials), a single Wati config row is shared across
 * all Wati-enabled orgs and stored under the platform org. The config blob looks like:
 * {
 *   "apiUrl": "https://live-server-XXXXX.wati.io",
 *   "apiKey": "<bearer-token>",
 *   "avniSystemUser": "<avni-user-for-scheduled-jobs>",
 *   "templateParamNames": {
 *     "chlorine_refill_reminder": ["name"],
 *     "weekly_payment_summary_chlorine_refill": ["name", "total_submissions", ...]
 *   }
 * }
 */
public class WatiSystemConfig {

    private static final String API_URL = "apiUrl";
    private static final String API_KEY = "apiKey";
    private static final String AVNI_SYSTEM_USER = "avniSystemUser";
    private static final String TEMPLATE_PARAM_NAMES = "templateParamNames";

    private final ExternalSystemConfig externalSystemConfig;

    public WatiSystemConfig(ExternalSystemConfig externalSystemConfig) {
        this.externalSystemConfig = externalSystemConfig;
    }

    public String getApiUrl() {
        return get(API_URL);
    }

    public String getApiKey() {
        return get(API_KEY);
    }

    public String getAvniSystemUser() {
        return get(AVNI_SYSTEM_USER);
    }

    /**
     * Returns the ordered list of Wati template parameter names for a given template.
     * These names are zipped with the parameters[] array at send time to build
     * named Wati parameters e.g. [{"name": "patient_name", "value": "Ramesh"}].
     * Returns null if no mapping is configured for the template.
     */
    @SuppressWarnings("unchecked")
    public List<String> getTemplateParamNames(String templateName) {
        Map<String, List<String>> templateParamNames =
                (Map<String, List<String>>) externalSystemConfig.getConfig().get(TEMPLATE_PARAM_NAMES);
        if (templateParamNames == null) return null;
        return templateParamNames.get(templateName);
    }

    private String get(String key) {
        return (String) externalSystemConfig.getConfig().get(key);
    }
}
