package org.avni.server.web.api;

public class ApiRequestContextHolder {
    private static ThreadLocal<ApiRequestContext> apiRequestContext = new ThreadLocal<>();

    private ApiRequestContextHolder() {
    }

    public static void create(ApiRequestContext context) {
        apiRequestContext.set(context);
    }

    public static boolean isVersionGreaterThan(int version) {
        return apiRequestContext.get().versionGreaterThan(version);
    }
}
