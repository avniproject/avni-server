package org.avni.server.web.api;

public class ApiRequestContext {
    private int version;
    private final static int MAX_VERSION = 3;

    public ApiRequestContext(String version) {
        int currentVersion = Integer.valueOf(version);
        assert currentVersion <= MAX_VERSION && currentVersion > 0;

        this.version = currentVersion;
    }

    public boolean versionGreaterThan(int version) {
        return this.version > version;
    }
}
