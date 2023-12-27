package org.avni.server.web.api;

public class ApiRequestContext {
    private int version;
    private static int maxVersion = 2;

    public ApiRequestContext(String version) {
        int currentVersion = Integer.valueOf(version);
        assert currentVersion <= maxVersion;

        this.version = currentVersion;
    }

    public boolean versionGreaterThan(int version) {
        return this.version > version;
    }
}
