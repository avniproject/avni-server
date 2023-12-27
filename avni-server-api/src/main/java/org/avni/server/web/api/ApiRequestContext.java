package org.avni.server.web.api;

public class ApiRequestContext {
    private int version;
    private static int MAX_VERSION = 2;

    public ApiRequestContext(String version) {
        int currentVersion = Integer.valueOf(version);
        assert currentVersion <= MAX_VERSION && currentVersion > 0;

        this.version = currentVersion;
    }

    public boolean versionGreaterThan(int version) {
        return this.version > version;
    }
}
