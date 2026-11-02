package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.SoftAssertions;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.DashboardService;
import org.avni.server.service.OrganisationService;
import org.avni.server.service.builder.TestProgramService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Subject Type filter on a dashboard can carry a preset subject type and an optional preset program
 * (avniproject/avni-server#1102). The phone gets the filter from the filter sync, which writes out the stored JSON as it
 * is, so the preset has to come out of that sync under the names the phone reads. Every other filter type stores none.
 * The web app reads the preset back from the dashboard endpoint, and a bundle carries it to another organisation.
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
    @Autowired
    private DashboardRepository dashboardRepository;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private PlatformTransactionManager transactionManager;

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

    // The web app opens a dashboard with GET /web/dashboard/{id}. The save's own answer is not what is checked: this is a
    // second request, so the preset has to come out of what was stored.
    @Test
    public void presetReadsBackFromAFreshLoad() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));

        JsonNode openedFilterConfig = openedByTheWebApp(saved.path("id").longValue()).path("filters").path(0).path("filterConfig");

        assertCarriesThePreset(openedFilterConfig, "the dashboard opened by the web app");
    }

    // The bundle is how a configuration moves to another organisation, so the preset has to survive the trip out and back in.
    // The import job writes inside a transaction, and DashboardService.uploadDashboardFilters relies on it: it adds the
    // filters to a dashboard that is already saved, without saving them. So the import here runs in a TransactionTemplate.
    // The test itself is not transactional, because the web saves in this class each have to commit or roll back on their own.
    @Test
    public void bundleCarriesThePresetAndAnotherOrganisationRestoresIt() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));
        String dashboardUuid = saved.path("uuid").textValue();

        JsonNode exportedDashboard = dashboardWithUuid(mapper.readTree(reportDashboardJsonOfTheBundleExport()), dashboardUuid);

        assertCarriesThePreset(exportedDashboard.path("filters").path(0).path("filterConfig"), "the dashboard in the exported reportDashboard.json");

        // Only this dashboard goes in, as the one-element array the import job reads out of reportDashboard.json.
        DashboardBundleContract[] bundle = mapper.treeToValue(mapper.createArrayNode().add(exportedDashboard), DashboardBundleContract[].class);
        switchToUserOfAnotherOrganisation("a-demo-admin");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> dashboardService.saveDashboards(bundle));

        Dashboard restored = dashboardRepository.findByUuid(dashboardUuid);
        assertThat(restored).as("the dashboard in the other organisation").isNotNull();
        assertThat(restored.getOrganisationId()).as("organisation of the restored dashboard")
                .isEqualTo(UserContextHolder.getUserContext().getOrganisationId());
        List<DashboardFilter> restoredFilters = new ArrayList<>(restored.getDashboardFilters());
        assertThat(restoredFilters).as("filters of the restored dashboard").hasSize(1);
        DashboardFilter.DashboardFilterConfig restoredFilterConfig = restoredFilters.get(0).getFilterConfig();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(restoredFilterConfig.getPresetSubjectTypeUuid()).as("preset subject type of the restored filter").isEqualTo(PRESET_PATIENT);
            softly.assertThat(restoredFilterConfig.getPresetProgramUuid()).as("preset program of the restored filter").isEqualTo(PRESET_SCREENING);
        });

        // And the endpoint the web app opens, in the organisation that restored it.
        JsonNode openedFilterConfig = openedByTheWebApp(restored.getId()).path("filters").path(0).path("filterConfig");
        assertCarriesThePreset(openedFilterConfig, "the restored dashboard opened by the web app");
    }

    // A dashboard saved before the preset existed has a Subject Type filter with no preset keys. It reads back as no
    // preset, and the phone's sync entry for it has neither key.
    @Test
    public void dashboardSavedBeforeThePresetReadsBackWithNone() throws Exception {
        DashboardFilter filter = new DashboardFilter();
        filter.setUuid(UUID.randomUUID().toString());
        filter.setName("Screening type");
        filter.setFilterConfig(new JsonObject().with("type", "SubjectType"));
        Dashboard dashboard = new Dashboard();
        dashboard.setUuid(UUID.randomUUID().toString());
        dashboard.setName("Dashboard from before the preset");
        dashboard.addUpdateFilter(filter);
        Dashboard saved = dashboardRepository.save(dashboard);

        JsonNode openedFilterConfig = openedByTheWebApp(saved.getId()).path("filters").path(0).path("filterConfig");
        JsonNode syncedFilterConfig = filterFromTheSync(filter.getUuid()).path("filterConfig");

        SoftAssertions.assertSoftly(softly -> {
            // The type is asserted too: a filter config that came back empty would also have no preset.
            softly.assertThat(openedFilterConfig.path("type").textValue()).as("type of the filter opened by the web app").isEqualTo("SubjectType");
            softly.assertThat(openedFilterConfig.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type opened by the web app: " + openedFilterConfig).isNull();
            softly.assertThat(openedFilterConfig.path("presetProgramUUID").textValue())
                    .as("preset program opened by the web app: " + openedFilterConfig).isNull();
            softly.assertThat(syncedFilterConfig.path("type").textValue()).as("type of the filter in the sync").isEqualTo("SubjectType");
            softly.assertThat(syncedFilterConfig.has("presetSubjectTypeUUID"))
                    .as("filter in the sync has presetSubjectTypeUUID: " + syncedFilterConfig).isFalse();
            softly.assertThat(syncedFilterConfig.has("presetProgramUUID"))
                    .as("filter in the sync has presetProgramUUID: " + syncedFilterConfig).isFalse();
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

    // The dashboard as the web app opens it, from the organisation of the current user.
    private JsonNode openedByTheWebApp(long dashboardId) throws Exception {
        String body = mockMvc.perform(get("/web/dashboard/" + dashboardId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    // Soft, so one failure names every key that is missing. The type is asserted too: a filter config that is not there
    // at all would also have no wrong preset, but it would not have the right one either.
    private void assertCarriesThePreset(JsonNode filterConfig, String where) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(filterConfig.path("type").textValue()).as("type of the filter in " + where).isEqualTo("SubjectType");
            softly.assertThat(filterConfig.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type in " + where + ": " + filterConfig).isEqualTo(PRESET_PATIENT);
            softly.assertThat(filterConfig.path("presetProgramUUID").textValue())
                    .as("preset program in " + where + ": " + filterConfig).isEqualTo(PRESET_SCREENING);
        });
    }

    // A user can be looked up only from its own organisation, so the next user is looked up with no organisation's role in force.
    private void switchToUserOfAnotherOrganisation(String username) {
        UserContextHolder.clear();
        setUser(username);
    }

    // The dashboards of the current organisation as a bundle download writes them, read back out of the zip.
    private String reportDashboardJsonOfTheBundleExport() throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zip)) {
            organisationService.addReportDashboard(zos);
        }
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                if (entry.getName().equals("reportDashboard.json")) {
                    return new String(entries.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("reportDashboard.json is not in the bundle export");
    }

    private JsonNode dashboardWithUuid(JsonNode dashboards, String dashboardUuid) {
        for (JsonNode dashboard : dashboards) {
            if (dashboardUuid.equals(dashboard.path("uuid").textValue())) {
                return dashboard;
            }
        }
        throw new AssertionError("the dashboard " + dashboardUuid + " is not among the exported dashboards: " + dashboards);
    }
}
