package org.avni.server.framework.security;

import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

public class ResourceProtectionStatus {
    private static final List<String> UnprotectedResources = Arrays.asList(
            "/cognito-details",
            "/idp-details",
            "/favicon.ico",
            "/static/**",
            "/userReview",
            "/asset-manifest.json",
            "/bulkuploads/**",
            "/documentation/**",
            "/icons/**",
            "/index.html",
            "/manifest.json",
            "/precache-manifest*",
            "/service-worker.js"
    );

    public static boolean isProtected(HttpServletRequest request) {
        return !isUnProtected(request);
    }

    public static boolean isUnProtected(HttpServletRequest request) {
        return UnprotectedResources.stream().anyMatch(pattern -> new AntPathRequestMatcher(pattern).matches(request));
    }
}
