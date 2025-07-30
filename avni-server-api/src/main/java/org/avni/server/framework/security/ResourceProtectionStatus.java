package org.avni.server.framework.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.Arrays;
import java.util.List;

public class ResourceProtectionStatus {
    private static final List<String> UnprotectedResources = Arrays.asList(
            "/",
            "/cognito-details",
            "/idp-details",
            "/favicon.ico",
            "/assets/**",
            "/asset-manifest.json",
            "/bulkuploads/**",
            "/documentation/**",
            "/icons/**",
            "/index.html",
            "/manifest.json",
            "/precache-manifest*",
            "/service-worker.js",
            "/ping",
            "/web/media",
            "/config",
            "/api/user/generateToken"
    );

    public static boolean isProtected(HttpServletRequest request) {
        return !isUnProtected(request);
    }

    public static boolean isUnProtected(HttpServletRequest request) {
        return UnprotectedResources.stream().anyMatch(pattern -> matches(request, pattern));
    }

    private static boolean matches(HttpServletRequest request, String pattern) {
        return new AntPathRequestMatcher(pattern).matches(request);
    }

    public static boolean isPresentIn(HttpServletRequest request, List<String> blacklistedUrls) {
        return blacklistedUrls.stream().anyMatch(pattern -> matches(request, pattern));
    }
}
