package org.avni.server.web.response.metabase;

public class SetupToggleResponse {
    private boolean success;
    private String message;

    public SetupToggleResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
