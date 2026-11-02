package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.SoftAssertions;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestProgramService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Subject Type filter on a dashboard can carry a preset subject type and an optional preset program
 * (avniproject/avni-server#1102). The phone gets the filter from the filter sync, which writes out the stored JSON as it
 * is, so the preset has to come out of that sync under the names the phone reads. Every other filter type stores none.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class DashboardFilterPresetIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String PRESET_PATIENT = "preset-patient";
    private static final String PRESET_SCREENING = "preset-screening";
    private static final String PRESET_TREATMENT = "preset-treatment";
    private static final String PRESET_HOUSEHOLD = "preset-household";
    private static final String PRESET_SURVEY = "preset-survey";

    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;

    // The subject types and programs are made as demo-admin, through the services that also give each one the
    // operational record and the form mapping an App Designer subject type or program has. They are real in this
    // organisation, so a save that checks its preset against them accepts them.
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
        SubjectType patient = createSubjectType(PRESET_PATIENT, "Preset Patient");
        createProgram(PRESET_SCREENING, "Preset Screening", patient);
        createProgram(PRESET_TREATMENT, "Preset Treatment", patient);
        SubjectType household = createSubjectType(PRESET_HOUSEHOLD, "Preset Household");
        createProgram(PRESET_SURVEY, "Preset Survey", household);
    }

    @Test
    public void subjectTypeFilterPresetReachesThePhoneInTheSync() throws Exception {
        JsonNode dashboard = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));
        String filterUuid = dashboard.path("filters").path(0).path("uuid").textValue();

        JsonNode syncedFilterConfig = filterFromTheSync(filterUuid).path("filterConfig");

        // Soft, so one failure names every key the phone would miss.
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(syncedFilterConfig.path("type").textValue()).as("type of the filter in the sync").isEqualTo("SubjectType");
            softly.assertThat(syncedFilterConfig.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type in the sync: " + syncedFilterConfig).isEqualTo(PRESET_PATIENT);
            softly.assertThat(syncedFilterConfig.path("presetProgramUUID").textValue())
                    .as("preset program in the sync: " + syncedFilterConfig).isEqualTo(PRESET_SCREENING);
        });
    }

    // Both presets go on the request of a date filter. They are a valid pair, so only the filter's type can be what keeps them out.
    @Test
    public void dateFilterWithPresetSettingsStoresNone() throws Exception {
        JsonNode dashboard = postDashboard(dashboardJson("Registration date dashboard", "RegistrationDate", PRESET_PATIENT, PRESET_SCREENING));
        String filterUuid = dashboard.path("filters").path(0).path("uuid").textValue();

        JsonNode syncedFilterConfig = filterFromTheSync(filterUuid).path("filterConfig");

        SoftAssertions.assertSoftly(softly -> {
            // The type is asserted too: a filter config missing from the sync entry altogether would also have neither key.
            softly.assertThat(syncedFilterConfig.path("type").textValue()).as("type of the filter in the sync").isEqualTo("RegistrationDate");
            softly.assertThat(syncedFilterConfig.has("presetSubjectTypeUUID"))
                    .as("date filter in the sync has presetSubjectTypeUUID: " + syncedFilterConfig).isFalse();
            softly.assertThat(syncedFilterConfig.has("presetProgramUUID"))
                    .as("date filter in the sync has presetProgramUUID: " + syncedFilterConfig).isFalse();
        });
    }

    private SubjectType createSubjectType(String uuid, String name) {
        return testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(uuid).setName(name).build());
    }

    private void createProgram(String uuid, String name, SubjectType subjectType) {
        testProgramService.addProgramAndGetFormMapping(new ProgramBuilder().withName(name).withUuid(uuid).build(), subjectType);
    }

    // A dashboard with no sections and one filter. It is built from plain JSON nodes, with the keys written out here
    // instead of taken from the request classes, so a wrong property name on the server cannot go unseen. A preset
    // that is null is left out of the request, and one that is "" is sent as "".
    private String dashboardJson(String dashboardName, String filterType, String presetSubjectTypeUuid, String presetProgramUuid) throws Exception {
        ObjectNode filterConfig = mapper.createObjectNode().put("type", filterType);
        if (presetSubjectTypeUuid != null) {
            filterConfig.put("presetSubjectTypeUUID", presetSubjectTypeUuid);
        }
        if (presetProgramUuid != null) {
            filterConfig.put("presetProgramUUID", presetProgramUuid);
        }
        ObjectNode filter = mapper.createObjectNode().put("name", "Screening type");
        filter.set("filterConfig", filterConfig);

        ObjectNode dashboard = mapper.createObjectNode().put("name", dashboardName);
        dashboard.putArray("sections");
        dashboard.putArray("filters").add(filter);
        return mapper.writeValueAsString(dashboard);
    }

    // Saves a dashboard as the current user, which has to succeed, and returns the dashboard the server answered with.
    // Qualified because the base class has its own post(path, json), which hides the static import.
    private JsonNode postDashboard(String json) throws Exception {
        String saved = mockMvc.perform(MockMvcRequestBuilders.post("/web/dashboard").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(saved);
    }

    // The filter sync, read from the beginning of time. The filters are under _embedded.dashboardFilter.
    private JsonNode filterFromTheSync(String filterUuid) throws Exception {
        String body = mockMvc.perform(get("/v2/dashboardFilter/search/lastModified")
                        .param("lastModifiedDateTime", "2000-01-01T00:00:00.000Z")
                        .param("now", DateTime.now(DateTimeZone.UTC).plusMinutes(1).toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode filter : mapper.readTree(body).path("_embedded").path("dashboardFilter")) {
            if (filterUuid.equals(filter.path("uuid").textValue())) {
                return filter;
            }
        }
        throw new AssertionError("the saved filter " + filterUuid + " is not under _embedded.dashboardFilter in the filter sync: " + body);
    }
}
