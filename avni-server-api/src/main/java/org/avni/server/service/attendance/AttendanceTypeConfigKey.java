package org.avni.server.service.attendance;

import org.avni.server.domain.JsonObject;
import org.avni.server.util.BadRequestError;

import java.util.Arrays;
import java.util.List;

public final class AttendanceTypeConfigKey {
    public static final String SESSION_OUTCOME_REASON_CONCEPT = "sessionOutcomeReasonConcept";
    public static final String ABSENCE_REASON_CONCEPT = "absenceReasonConcept";
    public static final String OTHER_REASON_CONCEPT = "otherReasonConcept";
    public static final String FOLLOW_UP_ENCOUNTER_TYPE = "followUpEncounterType";
    public static final String SHARE_RULE = "shareRule";
    public static final String AUTO_SHARE_ON_SAVE = "autoShareOnSave";

    // Display-only counterparts the webapp may include alongside the UUID fields. Server stores them
    // verbatim in JSONB but does not interpret or validate them.
    public static final String SESSION_OUTCOME_REASON_CONCEPT_NAME = "sessionOutcomeReasonConceptName";
    public static final String ABSENCE_REASON_CONCEPT_NAME = "absenceReasonConceptName";
    public static final String FOLLOW_UP_ENCOUNTER_TYPE_NAME = "followUpEncounterTypeName";

    public static final List<String> ALL = Arrays.asList(
            SESSION_OUTCOME_REASON_CONCEPT,
            ABSENCE_REASON_CONCEPT,
            OTHER_REASON_CONCEPT,
            FOLLOW_UP_ENCOUNTER_TYPE,
            SHARE_RULE,
            AUTO_SHARE_ON_SAVE,
            SESSION_OUTCOME_REASON_CONCEPT_NAME,
            ABSENCE_REASON_CONCEPT_NAME,
            FOLLOW_UP_ENCOUNTER_TYPE_NAME
    );

    public static final List<String> CONCEPT_UUID_KEYS = Arrays.asList(
            SESSION_OUTCOME_REASON_CONCEPT,
            ABSENCE_REASON_CONCEPT,
            OTHER_REASON_CONCEPT
    );

    public static final List<String> ENCOUNTER_TYPE_UUID_KEYS = List.of(
            FOLLOW_UP_ENCOUNTER_TYPE
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
