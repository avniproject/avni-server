package org.avni.server.service.attendance;

import org.avni.server.domain.JsonObject;
import org.avni.server.util.BadRequestError;

import java.util.Arrays;
import java.util.List;

public final class AttendanceTypeConfigKey {
    public static final String SESSION_OUTCOME_REASON_CONCEPT_UUID = "session_outcome_reason_concept_uuid";
    public static final String ABSENCE_REASON_CONCEPT_UUID = "absence_reason_concept_uuid";
    public static final String FOLLOW_UP_ENCOUNTER_TYPE_UUID = "follow_up_encounter_type_uuid";
    public static final String SHARE_RULE = "share_rule";
    public static final String AUTO_SHARE_ON_SAVE = "auto_share_on_save";

    public static final List<String> ALL = Arrays.asList(
            SESSION_OUTCOME_REASON_CONCEPT_UUID,
            ABSENCE_REASON_CONCEPT_UUID,
            FOLLOW_UP_ENCOUNTER_TYPE_UUID,
            SHARE_RULE,
            AUTO_SHARE_ON_SAVE
    );

    public static final List<String> CONCEPT_UUID_KEYS = Arrays.asList(
            SESSION_OUTCOME_REASON_CONCEPT_UUID,
            ABSENCE_REASON_CONCEPT_UUID
    );

    public static final List<String> ENCOUNTER_TYPE_UUID_KEYS = List.of(
            FOLLOW_UP_ENCOUNTER_TYPE_UUID
    );

    private AttendanceTypeConfigKey() {
    }

    public static String stringValue(JsonObject config, String key) {
        if (config == null) {
            return null;
        }
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new BadRequestError("AttendanceType.config.%s must be a string, got %s", key, value.getClass().getSimpleName());
        }
        return (String) value;
    }
}
