package org.avni.server.domain;

import org.avni.server.util.CollectionUtil;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UserSettings {
    private final JsonObject jsonObject;
    public static final String ID_PREFIX = "idPrefix";
    public static final String DATE_PICKER_MODE = "datePickerMode";
    public static final String LOCALE = "locale";
    public static final String ENABLE_BENEFICIARY_MODE = "showBeneficiaryMode";
    public static final String TRACK_LOCATION = "trackLocation";
    public static final String IS_ALLOWED_TO_INVOKE_TOKEN_GENERATION_API = "isAllowedToInvokeTokenGenerationAPI";
    public static final String DEFAULT_DATE_PICKER_MODE = "calendar";
    public static final String SPINNER_DATE_PICKER_MODE = "spinner";
    public static final List<String> DATE_PICKER_MODE_OPTIONS = Arrays.asList(DEFAULT_DATE_PICKER_MODE, SPINNER_DATE_PICKER_MODE);

    public UserSettings(JsonObject jsonObject) {
        this.jsonObject = (Objects.isNull(jsonObject)) ? new JsonObject() : jsonObject;
    }

    public static String createDatePickerMode(String datePickerMode) {
        if (StringUtils.isEmpty(datePickerMode)) {
            return DEFAULT_DATE_PICKER_MODE;
        }
        return CollectionUtil.findMatchingIgnoreCase(DATE_PICKER_MODE_OPTIONS, datePickerMode);
    }

    public static Boolean createTrackLocation(Boolean trackLocation) {
        return trackLocation != null && trackLocation;
    }

    public static Boolean createIsAllowedToInvokeTokenGenerationAPI(Boolean isAllowedToInvokeTokenGenerationAPI) {
        return isAllowedToInvokeTokenGenerationAPI != null && isAllowedToInvokeTokenGenerationAPI;
    }

    public static Boolean createEnableBeneficiaryMode(Boolean enableBeneficiaryMode) {
        return enableBeneficiaryMode != null && enableBeneficiaryMode;
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

    public boolean isAllowedToInvokeTokenGenerationAPI() {
        return jsonObject.containsKey(IS_ALLOWED_TO_INVOKE_TOKEN_GENERATION_API) && jsonObject.getBoolean(IS_ALLOWED_TO_INVOKE_TOKEN_GENERATION_API);
    }
}
