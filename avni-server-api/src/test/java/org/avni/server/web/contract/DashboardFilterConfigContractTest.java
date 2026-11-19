package org.avni.server.web.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter.FilterType;
import org.avni.server.web.contract.reports.DashboardFilterConfigBundleContract;
import org.avni.server.web.request.reports.DashboardFilterConfigRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * A Subject Type filter can carry a preset subject type and an optional preset program (avniproject/avni-server#1102).
 * They are two keys in the filter's stored JSON, and the filter sync hands that JSON to the phone as it is, so the key
 * names are written out here instead of read from the constants. Only a Subject Type filter stores them.
 */
public class DashboardFilterConfigContractTest {
    private static final String SUBJECT_TYPE_KEY = "subjectTypeUUID";
    private static final String PRESET_SUBJECT_TYPE_KEY = "presetSubjectTypeUUID";
    private static final String PRESET_PROGRAM_KEY = "presetProgramUUID";

    @Test
    public void subjectTypeFilterWritesBothPresetKeys() {
        JsonObject json = requestOfType(FilterType.SubjectType, "st-1", "pr-1").toJsonObject();

        assertEquals("st-1", json.getString(PRESET_SUBJECT_TYPE_KEY));
        assertEquals("pr-1", json.getString(PRESET_PROGRAM_KEY));
        // The request sets no subjectTypeUUID, the filter's existing key, so it has to stay empty: a preset must not land in it.
        // The contract writes that key as a null value, so the check is on the value.
        assertNull("subjectTypeUUID in " + json, json.getString(SUBJECT_TYPE_KEY));
    }

    @Test
    public void subjectTypeFilterWithoutPresetWritesNoPresetKeys() {
        JsonObject json = requestOfType(FilterType.SubjectType, null, null).toJsonObject();

        assertEquals("SubjectType", json.getString("type"));
        assertNoPresetKeys(json, "a Subject Type filter with no preset");
    }

    // A cleared picker may send empty text instead of leaving the value out. Each field is tried with both blank shapes.
    @Test
    public void blankPresetIsWrittenAsNoPreset() {
        assertNoPresetKeys(requestOfType(FilterType.SubjectType, "", "  ").toJsonObject(), "presets \"\" and \"  \"");
        assertNoPresetKeys(requestOfType(FilterType.SubjectType, "  ", "").toJsonObject(), "presets \"  \" and \"\"");
    }

    @Test
    public void presetValuesAreTrimmedWhenWritten() {
        JsonObject json = requestOfType(FilterType.SubjectType, " st-1 ", "  pr-1").toJsonObject();

        assertEquals("st-1", json.getString(PRESET_SUBJECT_TYPE_KEY));
        assertEquals("pr-1", json.getString(PRESET_PROGRAM_KEY));
    }

    @Test
    public void otherFilterTypesWriteNoPresetKeys() {
        for (FilterType filterType : new FilterType[]{FilterType.RegistrationDate, FilterType.AsOnDate}) {
            JsonObject json = requestOfType(filterType, "st-1", "pr-1").toJsonObject();

            assertEquals(filterType.name(), json.getString("type"));
            assertNoPresetKeys(json, "a " + filterType + " filter given presets");
        }
    }

    @Test
    public void bundleConfigWritesThePresetToo() {
        DashboardFilterConfigBundleContract config = new DashboardFilterConfigBundleContract();
        config.setType(FilterType.SubjectType.name());
        config.setPresetSubjectTypeUUID("st-1");
        config.setPresetProgramUUID("pr-1");

        JsonObject json = config.toJsonObject();

        assertEquals("st-1", json.getString(PRESET_SUBJECT_TYPE_KEY));
        assertEquals("pr-1", json.getString(PRESET_PROGRAM_KEY));
    }

    // A plain ObjectMapper writes a null as a null, so this fails unless the contract itself leaves the preset out.
    @Test
    public void bundleJsonLeavesOutAnAbsentPreset() throws Exception {
        DashboardFilterConfigBundleContract config = new DashboardFilterConfigBundleContract();
        config.setType(FilterType.SubjectType.name());

        ObjectMapper plainMapper = new ObjectMapper();
        JsonNode json = plainMapper.readTree(plainMapper.writeValueAsString(config));

        assertEquals("SubjectType", json.path("type").textValue());
        assertFalse("bundle JSON has " + PRESET_SUBJECT_TYPE_KEY + ": " + json, json.has(PRESET_SUBJECT_TYPE_KEY));
        assertFalse("bundle JSON has " + PRESET_PROGRAM_KEY + ": " + json, json.has(PRESET_PROGRAM_KEY));
    }

    private static DashboardFilterConfigRequest requestOfType(FilterType filterType, String presetSubjectTypeUuid, String presetProgramUuid) {
        DashboardFilterConfigRequest config = new DashboardFilterConfigRequest();
        config.setType(filterType.name());
        config.setPresetSubjectTypeUUID(presetSubjectTypeUuid);
        config.setPresetProgramUUID(presetProgramUuid);
        return config;
    }

    private static void assertNoPresetKeys(JsonObject json, String what) {
        assertFalse(what + " stored " + PRESET_SUBJECT_TYPE_KEY + ": " + json, json.containsKey(PRESET_SUBJECT_TYPE_KEY));
        assertFalse(what + " stored " + PRESET_PROGRAM_KEY + ": " + json, json.containsKey(PRESET_PROGRAM_KEY));
    }
}
