package org.avni.server.service.abdm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// This deployment's ABDM values, from application.properties (abdm.*). Everything an organisation sets is in its Eka
// settings (EkaSystemConfig), read through AbdmSettings.
@Component
public class AbdmConfig {
    private final String publicBaseUrl;

    public AbdmConfig(@Value("${abdm.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = StringUtils.hasText(publicBaseUrl) ? trimSlash(publicBaseUrl.trim()) : null;
    }

    // The address Eka posts an organisation's events to; null until both this server's public address and the
    // organisation's webhook ID are known.
    public String webhookUrl(String webhookId) {
        if (publicBaseUrl == null || !StringUtils.hasText(webhookId)) return null;
        return publicBaseUrl + AbdmPaths.IN_EKA_WEBHOOK + "/" + webhookId;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
