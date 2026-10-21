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
    public static final String DISABLE_AUTO_SYNC = "disableAutoSync";
    public static final String ABHA_HIP_ID = "abhaHipId";
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

    /**
     * Whole-settings writes replace everything, but a user's health centre is set elsewhere (the upload column, the
     * admin app field) and must survive a write that does not carry it. An empty string clears it.
     */
    public static JsonObject mergeKeepingAbhaHipId(JsonObject existing, JsonObject incoming) {
        JsonObject merged = incoming == null ? new JsonObject() : new JsonObject(incoming);
        if (!merged.containsKey(ABHA_HIP_ID)) {
            if (existing != null && existing.containsKey(ABHA_HIP_ID)) {
                merged.put(ABHA_HIP_ID, existing.get(ABHA_HIP_ID));
            }
        } else if ("".equals(merged.get(ABHA_HIP_ID))) {
            merged.remove(ABHA_HIP_ID);
        }
        return merged;
    }

    /**
     * A user's own settings write, from the phone: the whole stored copy the phone holds, sent whenever the user changes,
     * say, the language. The health centre is set only by an admin, so the stored one is always kept and an incoming
     * one, set, changed or empty, is ignored; a stale copy must not undo an admin's reassignment.
     */
    public static JsonObject mergeKeepingStoredAbhaHipId(JsonObject existing, JsonObject incoming) {
        JsonObject merged = incoming == null ? new JsonObject() : new JsonObject(incoming);
        merged.remove(ABHA_HIP_ID);
        if (existing != null && existing.containsKey(ABHA_HIP_ID)) {
            merged.put(ABHA_HIP_ID, existing.get(ABHA_HIP_ID));
        }
        return merged;
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
