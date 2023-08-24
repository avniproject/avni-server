package org.avni.server.domain;

public class UserSettings {
    private final JsonObject jsonObject;
    public static final String ID_PREFIX = "idPrefix";

    public UserSettings(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public String getIdPrefix() {
        return UserSettings.getIdPrefix(this.jsonObject);
    }

    public static String getIdPrefix(JsonObject jsonObject) {
        if (jsonObject == null) return null;
        return jsonObject.getString(ID_PREFIX);
    }
}
