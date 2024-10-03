package org.avni.server.domain;

import org.avni.server.util.CollectionUtil;

import java.util.Arrays;
import java.util.List;

public class UserSettings {
    private final JsonObject jsonObject;
    public static final String ID_PREFIX = "idPrefix";
    public static final String DATE_PICKER_MODE = "datePickerMode";
    public static final String LOCALE = "locale";
    public static final String ENABLE_BENEFICIARY_MODE = "showBeneficiaryMode";
    public static final String TRACK_LOCATION = "trackLocation";
    public static final List<String> DATE_PICKER_MODE_OPTIONS = Arrays.asList("calendar", "spinner");

    public UserSettings(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public static String createDatePickerMode(String datePickerMode) {
        return CollectionUtil.findMatchingIgnoreCase(DATE_PICKER_MODE_OPTIONS, datePickerMode);
    }

    public String getIdPrefix() {
        return UserSettings.getIdPrefix(this.jsonObject);
    }

    public static String getIdPrefix(JsonObject jsonObject) {
        if (jsonObject == null) return null;
        return jsonObject.getString(ID_PREFIX);
    }

    public String getDatePickerMode() {
        return jsonObject.getString(DATE_PICKER_MODE);
    }

    public String getLocale() {
        return jsonObject.getString(LOCALE);
    }

    public boolean isEnableBeneficiaryMode() {
        return jsonObject.getBoolean(ENABLE_BENEFICIARY_MODE);
    }

    public boolean isTrackLocation() {
        return jsonObject.getBoolean(TRACK_LOCATION);
    }
}
