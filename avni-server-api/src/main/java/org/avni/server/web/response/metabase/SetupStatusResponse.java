package org.avni.server.web.response.metabase;

public class SetupStatusResponse {
    private boolean setupEnabled;

    public SetupStatusResponse(boolean setupEnabled) {
        this.setupEnabled = setupEnabled;
    }

    public boolean isSetupEnabled() {
        return setupEnabled;
    }
}
