package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.SoftAssertions;
import org.avni.server.application.FormMapping;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OperationalProgram;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.DashboardService;
import org.avni.server.service.FormMappingParameterObject;
import org.avni.server.service.FormMappingService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Subject Type filter on a dashboard can carry a preset subject type and an optional preset program
 * (avniproject/avni-server#1102). The phone gets the filter from the filter sync, which writes out the stored JSON as it
 * is, so the preset has to come out of that sync under the names the phone reads. Every other filter type stores none.
 * The web app reads the preset back from the dashboard endpoint, and a bundle carries it to another organisation.
 * A web save refuses a preset that is incomplete, missing, not used in the organisation, or a program the subject type
 * is not enrolled into. Only the web save is checked: a bundle carries its own consistency. An edit is checked the same way and
 * is refused whole, and the phone is given a changed or cleared preset by its next sync.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class DashboardFilterPresetIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String PRESET_PATIENT = "preset-patient";
    private static final String PRESET_SCREENING = "preset-screening";
    private static final String PRESET_TREATMENT = "preset-treatment";
    private static final String PRESET_HOUSEHOLD = "preset-household";
    private static final String PRESET_SURVEY = "preset-survey";
    // OpenCHS, the parent of demo and of a-demo in test-data.sql.
    private static final Long PARENT_ORGANISATION_ID = 1L;

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
    @Autowired
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Autowired
    private OperationalProgramRepository operationalProgramRepository;
    @Autowired
    private FormMappingService formMappingService;
    @Autowired
    private FormMappingRepository formMappingRepository;
    @Autowired
    private DataSource dataSource;

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

    // A bundle uploaded again into an organisation that already has the dashboard replaces the filter's preset: a changed
    // program arrives, and a bundle with no preset clears it. Phones get each change on their next sync. Every upload from a
    // test organisation to production after the first one takes this path.
    @Test
    public void bundleUploadedAgainChangesAndClearsThePreset() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));
        String filterUuid = saved.path("filters").path(0).path("uuid").textValue();
        ObjectNode exportedDashboard = (ObjectNode) dashboardWithUuid(mapper.readTree(reportDashboardJsonOfTheBundleExport()), saved.path("uuid").textValue());
        ObjectNode exportedFilterConfig = (ObjectNode) exportedDashboard.path("filters").path(0).path("filterConfig");

        switchToUserOfAnotherOrganisation("a-demo-admin");
        importIntoThisOrganisation(exportedDashboard);
        DateTime firstImportAt = lastModifiedOf(filterFromTheSync(filterUuid));

        exportedFilterConfig.put("presetProgramUUID", PRESET_TREATMENT);
        importIntoThisOrganisation(exportedDashboard);
        JsonNode changedEntry = filterFromTheSync(filterUuid, firstImportAt.plusMillis(1));
        DateTime changedAt = lastModifiedOf(changedEntry);

        exportedFilterConfig.remove("presetSubjectTypeUUID");
        exportedFilterConfig.remove("presetProgramUUID");
        importIntoThisOrganisation(exportedDashboard);
        JsonNode clearedEntry = filterFromTheSync(filterUuid, changedAt.plusMillis(1));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(changedEntry.path("filterConfig").path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type in the sync after the bundle with a changed program: " + changedEntry).isEqualTo(PRESET_PATIENT);
            softly.assertThat(changedEntry.path("filterConfig").path("presetProgramUUID").textValue())
                    .as("preset program in the sync after the bundle with a changed program: " + changedEntry).isEqualTo(PRESET_TREATMENT);
        });
        assertCarriesNoPreset(clearedEntry.path("filterConfig"), "SubjectType", "the filter in the sync after a bundle with no preset");
    }

    // One dashboard, imported as the import job reads it out of reportDashboard.json, inside a transaction as the job runs.
    private void importIntoThisOrganisation(JsonNode exportedDashboard) throws Exception {
        DashboardBundleContract[] bundle = mapper.treeToValue(mapper.createArrayNode().add(exportedDashboard), DashboardBundleContract[].class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> dashboardService.saveDashboards(bundle));
    }

    // A dashboard saved before the preset existed has a Subject Type filter with no preset keys. It reads back as no
    // preset, and the phone's sync entry for it has neither key.
    @Test
    public void dashboardSavedBeforeThePresetReadsBackWithNone() throws Exception {
        String filterUuid = UUID.randomUUID().toString();
        Dashboard saved = storeDashboardFromBeforeThePreset(filterUuid);

        JsonNode openedFilterConfig = openedByTheWebApp(saved.getId()).path("filters").path(0).path("filterConfig");
        JsonNode syncedFilterConfig = filterFromTheSync(filterUuid).path("filterConfig");

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

    // A cleared program picker sends the program as empty text or as spaces. That is no program: the subject type alone is
    // saved.
    @Test
    public void presetSubjectTypeWithABlankProgramSavesWithNoProgram() throws Exception {
        for (String blankProgram : new String[]{"", "   "}) {
            JsonNode saved = postDashboard(dashboardJson("Patient dashboard, program '" + blankProgram + "'", "SubjectType", PRESET_PATIENT, blankProgram));
            JsonNode syncedFilterConfig = filterFromTheSync(saved.path("filters").path(0).path("uuid").textValue()).path("filterConfig");

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(syncedFilterConfig.path("presetSubjectTypeUUID").textValue())
                        .as("preset subject type in the sync, program sent as '" + blankProgram + "': " + syncedFilterConfig).isEqualTo(PRESET_PATIENT);
                softly.assertThat(syncedFilterConfig.has("presetProgramUUID"))
                        .as("filter in the sync has presetProgramUUID, program sent as '" + blankProgram + "': " + syncedFilterConfig).isFalse();
            });
        }
    }

    // The same dashboard saved again unchanged, with the body built from its read-back. It has no preset and gets none, and
    // it keeps its filter: the answer carries the uuid of the filter that was stored.
    @Test
    public void dashboardSavedBeforeThePresetSavesUnchanged() throws Exception {
        String filterUuid = UUID.randomUUID().toString();
        Dashboard stored = storeDashboardFromBeforeThePreset(filterUuid);

        JsonNode answer = putDashboard(stored.getId(), openedByTheWebApp(stored.getId()));
        JsonNode syncedFilterConfig = filterFromTheSync(filterUuid).path("filterConfig");

        assertThat(answer.path("filters").path(0).path("uuid").textValue()).as("uuid of the filter in the answer to the save").isEqualTo(filterUuid);
        assertCarriesNoPreset(syncedFilterConfig, "SubjectType", "the filter in the sync after the save");
    }

    // The card says "optionally a preset program": a subject type alone is a whole preset.
    @Test
    public void presetSubjectTypeAloneSavesAndReadsBack() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Patient dashboard", "SubjectType", PRESET_PATIENT, null));
        String filterUuid = saved.path("filters").path(0).path("uuid").textValue();

        JsonNode openedFilterConfig = openedByTheWebApp(saved.path("id").longValue()).path("filters").path(0).path("filterConfig");
        JsonNode syncedFilterConfig = filterFromTheSync(filterUuid).path("filterConfig");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(openedFilterConfig.path("type").textValue()).as("type of the filter opened by the web app").isEqualTo("SubjectType");
            softly.assertThat(openedFilterConfig.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type opened by the web app: " + openedFilterConfig).isEqualTo(PRESET_PATIENT);
            softly.assertThat(openedFilterConfig.path("presetProgramUUID").textValue())
                    .as("preset program opened by the web app: " + openedFilterConfig).isNull();
            softly.assertThat(syncedFilterConfig.path("type").textValue()).as("type of the filter in the sync").isEqualTo("SubjectType");
            softly.assertThat(syncedFilterConfig.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type in the sync: " + syncedFilterConfig).isEqualTo(PRESET_PATIENT);
            softly.assertThat(syncedFilterConfig.has("presetProgramUUID"))
                    .as("filter in the sync has presetProgramUUID: " + syncedFilterConfig).isFalse();
        });
    }

    // A program is chosen within a subject type, so a preset program alone has nothing to be chosen within. A subject type
    // left empty, or with only spaces, is none.
    @Test
    public void presetProgramWithoutPresetSubjectTypeIsRefused() throws Exception {
        String reason = "Preset program needs a preset subject type, on filter Screening type";

        assertNewDashboardIsRefused("Program only dashboard", dashboardJson("Program only dashboard", "SubjectType", null, PRESET_SCREENING), reason);
        assertNewDashboardIsRefused("Empty subject type dashboard", dashboardJson("Empty subject type dashboard", "SubjectType", "", PRESET_SCREENING), reason);
        assertNewDashboardIsRefused("Spaces subject type dashboard", dashboardJson("Spaces subject type dashboard", "SubjectType", "   ", PRESET_SCREENING), reason);
    }

    // The filter's name goes into the message, and BadRequestError formats its message. A name that went into the format
    // itself would be read as a conversion at the "% s", and the refusal would turn into a 500.
    @Test
    public void filterNameWithAPercentSignIsRefusedWithItsName() throws Exception {
        assertNewDashboardIsRefused("Percent dashboard", dashboardJson("Percent dashboard", "100% screened", "SubjectType", null, PRESET_SCREENING),
                "Preset program needs a preset subject type, on filter 100% screened");
    }

    @Test
    public void missingPresetSubjectTypeIsRefusedNamingIt() throws Exception {
        assertNewDashboardIsRefused("Missing type dashboard", dashboardJson("Missing type dashboard", "SubjectType", "no-such-subject-type", null),
                "Preset subject type with uuid no-such-subject-type doesn't exist");
    }

    // The subject type is in the database, but no operational record makes it one this organisation uses.
    @Test
    public void presetSubjectTypeNotInUseHereIsRefused() throws Exception {
        subjectTypeNotInUse("preset-unused", "Preset Unused");

        assertNewDashboardIsRefused("Unused type dashboard", dashboardJson("Unused type dashboard", "SubjectType", "preset-unused", null),
                "Preset subject type 'Preset Unused' (preset-unused) is not available in this organisation");
    }

    @Test
    public void missingPresetProgramIsRefusedNamingIt() throws Exception {
        assertNewDashboardIsRefused("Missing program dashboard", dashboardJson("Missing program dashboard", "SubjectType", PRESET_PATIENT, "no-such-program"),
                "Preset program with uuid no-such-program doesn't exist");
    }

    // The program has no operational record, and no enrolment form mapping either. It is refused as not in use, which is
    // the check before the enrolment one.
    @Test
    public void presetProgramNotInUseHereIsRefused() throws Exception {
        programNotInUse("preset-unused-program", "Preset Unused Program");

        assertNewDashboardIsRefused("Unused program dashboard", dashboardJson("Unused program dashboard", "SubjectType", PRESET_PATIENT, "preset-unused-program"),
                "Preset program 'Preset Unused Program' (preset-unused-program) is not available in this organisation");
    }

    // A padded value is stored trimmed, so it has to be looked up trimmed as well.
    @Test
    public void paddedPresetValuesAreTrimmedBeforeTheCheck() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Padded dashboard", "SubjectType", " " + PRESET_PATIENT + " ", " " + PRESET_SCREENING + " "));

        JsonNode openedFilterConfig = openedByTheWebApp(saved.path("id").longValue()).path("filters").path(0).path("filterConfig");

        assertCarriesThePreset(openedFilterConfig, "the dashboard opened by the web app, saved with padded presets");
    }

    // "Preset Survey" is a program of this organisation, but it is enrolled into from "Preset Household", not from "Preset
    // Patient". "Preset Treatment" is the second program of "Preset Patient", so the first one is not the only one that passes.
    @Test
    public void programNotEnrolledFromThePresetSubjectTypeIsRefusedNamingBoth() throws Exception {
        assertNewDashboardIsRefused("Household program dashboard", dashboardJson("Household program dashboard", "SubjectType", PRESET_PATIENT, PRESET_SURVEY),
                "Preset program Preset Survey is not enrolled into from subject type Preset Patient");

        JsonNode saved = postDashboard(dashboardJson("Second program dashboard", "SubjectType", PRESET_PATIENT, PRESET_TREATMENT));
        assertThat(saved.path("filters").path(0).path("filterConfig").path("presetProgramUUID").textValue())
                .as("preset program of the saved dashboard").isEqualTo(PRESET_TREATMENT);
    }

    // The program, its operational record and the subject type are all still in use here. Only the enrolment mapping of the
    // pair is voided, so Preset Screening is no longer a program that Preset Patient enrols into.
    @Test
    public void programWhoseEnrolmentMappingWasVoidedIsRefused() throws Exception {
        voidEnrolmentMappingAsAppDesignerDoes(PRESET_PATIENT, PRESET_SCREENING);

        assertNewDashboardIsRefused("Unmapped program dashboard", dashboardJson("Unmapped program dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING),
                "Preset program Preset Screening is not enrolled into from subject type Preset Patient");
    }

    // A child organisation sees its parent's rows. When the parent organisation maps the same subject type and program as this
    // one, both mappings are visible here, so a lookup that expects one mapping answers 500 where the list of mappings does not.
    @Test
    public void enrolmentMappedInParentAndChildSaves() throws Exception {
        Long thisOrganisationId = UserContextHolder.getUserContext().getOrganisationId();
        mapEnrolmentInTheParentOrganisation(PRESET_PATIENT, PRESET_SCREENING);
        // Without both rows visible the save below proves nothing, so the set-up is checked first.
        assertThat(organisationsMappingEnrolmentAsSeenHere(PRESET_PATIENT, PRESET_SCREENING))
                .as("organisations that map Preset Patient to Preset Screening, among the mappings visible to this organisation")
                .containsExactlyInAnyOrder(PARENT_ORGANISATION_ID, thisOrganisationId);

        JsonNode saved = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));

        assertCarriesThePreset(openedByTheWebApp(saved.path("id").longValue()).path("filters").path(0).path("filterConfig"), "the dashboard opened by the web app");
    }

    // The other organisation's subject type is real, and it is in use there. This organisation cannot see it at all.
    @Test
    public void presetSubjectTypeOfAnotherOrganisationIsRefused() throws Exception {
        switchToUserOfAnotherOrganisation("a-demo-admin");
        createSubjectType("preset-elsewhere", "Preset Elsewhere");
        switchToUserOfAnotherOrganisation("demo-admin");

        assertNewDashboardIsRefused("Elsewhere dashboard", dashboardJson("Elsewhere dashboard", "SubjectType", "preset-elsewhere", null),
                "Preset subject type with uuid preset-elsewhere doesn't exist");
    }

    // The edit is built from the read-back: the dashboard as it was read, with the same dashboard and filter uuids, and only
    // the program changed to one the subject type is not enrolled into.
    @Test
    public void refusedEditKeepsTheStoredPreset() throws Exception {
        long dashboardId = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING)).path("id").longValue();
        ObjectNode edit = openedByTheWebApp(dashboardId).deepCopy();
        ((ObjectNode) edit.path("filters").path(0).path("filterConfig")).put("presetProgramUUID", PRESET_SURVEY);

        String body = putDashboardRefused(dashboardId, edit);

        assertThat(body).as("the answer to the refused edit").contains("Preset program Preset Survey is not enrolled into from subject type Preset Patient");
        assertCarriesThePreset(openedByTheWebApp(dashboardId).path("filters").path(0).path("filterConfig"), "the dashboard opened after the refused edit");
    }

    // A refused edit changes nothing. This one renames the dashboard and its first filter, and points the second filter's
    // preset at a program its subject type is not enrolled into, so the second filter is the one refused. The filters go back
    // in an order set here: the answer lists them from a set, and a check of the first filter alone must not let this through.
    @Test
    public void refusedEditKeepsTheDashboardAsItWas() throws Exception {
        long dashboardId = postDashboard(dashboardJsonWithFilters("Original dashboard", "Original description",
                filterJson("Registered on", "RegistrationDate", null, null),
                filterJson("Screening type", "SubjectType", PRESET_PATIENT, PRESET_SCREENING))).path("id").longValue();

        ObjectNode edit = openedByTheWebApp(dashboardId).deepCopy();
        ObjectNode registrationFilter = filterNamed(edit, "Registered on");
        ObjectNode presetFilter = filterNamed(edit, "Screening type");
        registrationFilter.put("name", "Registered on, renamed");
        ((ObjectNode) presetFilter.path("filterConfig")).put("presetProgramUUID", PRESET_SURVEY);
        edit.put("name", "Edited dashboard").put("description", "Edited description");
        edit.putArray("filters").add(registrationFilter).add(presetFilter);

        assertThat(putDashboardRefused(dashboardId, edit)).as("the answer to the refused edit")
                .contains("Preset program Preset Survey is not enrolled into from subject type Preset Patient");

        JsonNode afterwards = openedByTheWebApp(dashboardId);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(afterwards.path("name").textValue()).as("name of the dashboard after the refused edit").isEqualTo("Original dashboard");
            softly.assertThat(afterwards.path("description").textValue()).as("description of the dashboard after the refused edit").isEqualTo("Original description");
            softly.assertThat(namesOfFilters(afterwards)).as("names of the filters after the refused edit").containsExactlyInAnyOrder("Registered on", "Screening type");
            softly.assertThat(filterNamed(afterwards, "Screening type").path("filterConfig").path("presetProgramUUID").textValue())
                    .as("preset program of the second filter after the refused edit").isEqualTo(PRESET_SCREENING);
        });
        assertThat(namesOfListedDashboards()).as("dashboards listed after the refused edit").contains("Original dashboard").doesNotContain("Edited dashboard");
    }

    // The phone is given a filter by the filter sync, which hands out the filters changed since the time it asks from. An
    // edit has to move the filter's last modified time, or the phone would never be given the change. Each sync here asks from
    // 1 ms after the filter's previous time, so only a filter changed since then is in it.
    @Test
    public void changedAndClearedPresetsReachThePhoneOnTheNextSync() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));
        long dashboardId = saved.path("id").longValue();
        String filterUuid = saved.path("filters").path(0).path("uuid").textValue();
        DateTime savedAt = lastModifiedOf(filterFromTheSync(filterUuid));

        ObjectNode programChanged = openedByTheWebApp(dashboardId).deepCopy();
        ((ObjectNode) programChanged.path("filters").path(0).path("filterConfig")).put("presetProgramUUID", PRESET_TREATMENT);
        putDashboard(dashboardId, programChanged);

        JsonNode changedEntry = filterFromTheSync(filterUuid, savedAt.plusMillis(1));
        DateTime changedAt = lastModifiedOf(changedEntry);
        JsonNode changedConfig = changedEntry.path("filterConfig");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(changedAt.getMillis()).as("last modified time of the filter after its program was changed, saved at " + savedAt).isGreaterThan(savedAt.getMillis());
            softly.assertThat(changedConfig.path("type").textValue()).as("type of the filter in the sync").isEqualTo("SubjectType");
            softly.assertThat(changedConfig.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type in the sync after the program was changed: " + changedConfig).isEqualTo(PRESET_PATIENT);
            softly.assertThat(changedConfig.path("presetProgramUUID").textValue())
                    .as("preset program in the sync after the program was changed: " + changedConfig).isEqualTo(PRESET_TREATMENT);
        });

        ObjectNode presetCleared = openedByTheWebApp(dashboardId).deepCopy();
        ((ObjectNode) presetCleared.path("filters").path(0).path("filterConfig")).remove(List.of("presetSubjectTypeUUID", "presetProgramUUID"));
        putDashboard(dashboardId, presetCleared);

        assertCarriesNoPreset(filterFromTheSync(filterUuid, changedAt.plusMillis(1)).path("filterConfig"), "SubjectType", "the filter in the sync after the preset was cleared");
    }

    @Test
    public void voidedPresetSubjectTypeIsRefused() throws Exception {
        voidSubjectTypeAsAppDesignerDoes(PRESET_PATIENT);

        assertNewDashboardIsRefused("Voided type dashboard", dashboardJson("Voided type dashboard", "SubjectType", PRESET_PATIENT, null),
                "Preset subject type with uuid preset-patient doesn't exist");
    }

    @Test
    public void voidedPresetProgramIsRefused() throws Exception {
        voidProgramAsAppDesignerDoes(PRESET_SCREENING);

        assertNewDashboardIsRefused("Voided program dashboard", dashboardJson("Voided program dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING),
                "Preset program with uuid preset-screening doesn't exist");
    }

    // A program deleted in App Designer after a filter was saved with it. The filter still carries the preset, so the phone is
    // still given it (avni-client#2222 has to cope with that), and a save whose body is built from the read-back is refused
    // until the program is cleared from the filter.
    @Test
    public void presetVoidedAfterSavingStillSyncsAndBlocksAResave() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Screening dashboard", "SubjectType", PRESET_PATIENT, PRESET_SCREENING));
        long dashboardId = saved.path("id").longValue();
        String filterUuid = saved.path("filters").path(0).path("uuid").textValue();
        voidProgramAsAppDesignerDoes(PRESET_SCREENING);

        JsonNode syncedAfterVoiding = filterFromTheSync(filterUuid).path("filterConfig");
        String refusalOfTheResave = putDashboardRefused(dashboardId, openedByTheWebApp(dashboardId));
        ObjectNode withoutProgram = openedByTheWebApp(dashboardId).deepCopy();
        ((ObjectNode) withoutProgram.path("filters").path(0).path("filterConfig")).remove("presetProgramUUID");
        putDashboard(dashboardId, withoutProgram);
        JsonNode syncedAfterClearing = filterFromTheSync(filterUuid).path("filterConfig");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(syncedAfterVoiding.path("presetProgramUUID").textValue())
                    .as("preset program in the sync after the program was voided: " + syncedAfterVoiding).isEqualTo(PRESET_SCREENING);
            softly.assertThat(refusalOfTheResave).as("the answer to saving the dashboard as it was read back")
                    .contains("Preset program with uuid preset-screening doesn't exist");
            softly.assertThat(syncedAfterClearing.path("presetSubjectTypeUUID").textValue())
                    .as("preset subject type in the sync after the program was cleared: " + syncedAfterClearing).isEqualTo(PRESET_PATIENT);
            softly.assertThat(syncedAfterClearing.has("presetProgramUUID"))
                    .as("filter in the sync after the program was cleared has presetProgramUUID: " + syncedAfterClearing).isFalse();
        });
    }

    // A picker that has been cleared may send "", and one with a stray space may send only spaces. Neither is a preset, so
    // it is neither checked nor stored.
    @Test
    public void blankPresetsAreSavedAsNone() throws Exception {
        String emptyFilterUuid = postDashboard(dashboardJson("Blank presets dashboard", "SubjectType", "", ""))
                .path("filters").path(0).path("uuid").textValue();
        String spacesFilterUuid = postDashboard(dashboardJson("Spaces presets dashboard", "SubjectType", "   ", "   "))
                .path("filters").path(0).path("uuid").textValue();

        assertCarriesNoPreset(filterFromTheSync(emptyFilterUuid).path("filterConfig"), "SubjectType", "the filter saved with empty presets, in the sync");
        assertCarriesNoPreset(filterFromTheSync(spacesFilterUuid).path("filterConfig"), "SubjectType", "the filter saved with presets of only spaces, in the sync");
    }

    // Only a Subject Type filter is checked. Neither value here exists, so only the filter's type can be what lets the save through.
    @Test
    public void dateFilterWithAnInvalidPresetIsNotChecked() throws Exception {
        JsonNode saved = postDashboard(dashboardJson("Invalid preset on a date dashboard", "RegistrationDate", "no-such-subject-type", "no-such-program"));
        String filterUuid = saved.path("filters").path(0).path("uuid").textValue();

        JsonNode syncedFilterConfig = filterFromTheSync(filterUuid).path("filterConfig");

        SoftAssertions.assertSoftly(softly -> {
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

    // Saved through the repository and nothing else, so there is no operational record for it.
    private void subjectTypeNotInUse(String uuid, String name) {
        subjectTypeRepository.save(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().setUuid(uuid).setName(name).build());
    }

    private void programNotInUse(String uuid, String name) {
        programRepository.save(new ProgramBuilder().withName(name).withUuid(uuid).build());
    }

    // Deleting a subject type in App Designer voids its operational record and the subject type, then its form mappings
    // (SubjectTypeController.voidSubjectType). The names it also changes are left alone: nothing here reads them.
    private void voidSubjectTypeAsAppDesignerDoes(String subjectTypeUuid) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
            OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository
                    .findBySubjectTypeAndOrganisationId(subjectType, UserContextHolder.getUserContext().getOrganisationId());
            operationalSubjectType.setVoided(true);
            subjectType.setVoided(true);
            operationalSubjectTypeRepository.save(operationalSubjectType);
            subjectTypeRepository.save(subjectType);
            formMappingService.voidExistingFormMappings(new FormMappingParameterObject(subjectTypeUuid, null, null), null);
        });
    }

    // The same for a program (ProgramController.voidProgram).
    private void voidProgramAsAppDesignerDoes(String programUuid) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Program program = programRepository.findByUuid(programUuid);
            OperationalProgram operationalProgram = operationalProgramRepository
                    .findByProgramAndOrganisationId(program, UserContextHolder.getUserContext().getOrganisationId());
            operationalProgram.setVoided(true);
            program.setVoided(true);
            operationalProgramRepository.save(operationalProgram);
            programRepository.save(program);
            formMappingService.voidExistingFormMappings(new FormMappingParameterObject(null, programUuid, null), null);
        });
    }

    // Only the enrolment mapping of the pair is voided, as FormMappingService.createOrUpdateFormMapping does for a mapping saved
    // as voided. The program and its operational record are left as they are.
    private void voidEnrolmentMappingAsAppDesignerDoes(String subjectTypeUuid, String programUuid) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            FormMapping enrolmentMapping = formMappingRepository.getProgramEnrolmentFormMapping(
                    subjectTypeRepository.findByUuid(subjectTypeUuid), programRepository.findByUuid(programUuid));
            enrolmentMapping.setVoided(true);
            formMappingRepository.saveFormMapping(enrolmentMapping);
        });
    }

    // Maps the subject type to the program as the parent organisation, with an enrolment form of its own. The current user is put
    // back afterwards. No user belongs to the parent organisation: admin has no organisation. A connection for a user of this
    // organisation is refused a row of another organisation by row-level security. So these are plain inserts with the default
    // super admin in force, which has no organisation and so takes no database role, as RlsOrgVisibilityIntegrationTest does.
    private void mapEnrolmentInTheParentOrganisation(String subjectTypeUuid, String programUuid) {
        long subjectTypeId = subjectTypeRepository.findByUuid(subjectTypeUuid).getId();
        long programId = programRepository.findByUuid(programUuid).getId();
        String username = UserContextHolder.getUserContext().getUserName();
        switchToUserOfAnotherOrganisation("admin");
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long formId = jdbc.queryForObject("insert into form (name, form_type, uuid, version, organisation_id, is_voided, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                    "values (?, 'ProgramEnrolment', ?, 0, ?, false, 1, 1, now(), now()) returning id", Long.class, "Parent enrolment form", UUID.randomUUID().toString(), PARENT_ORGANISATION_ID);
            jdbc.update("insert into form_mapping (form_id, uuid, version, entity_id, subject_type_id, organisation_id, is_voided, enable_approval, impl_version, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                    "values (?, ?, 0, ?, ?, ?, false, false, 1, 1, 1, now(), now())", formId, UUID.randomUUID().toString(), programId, subjectTypeId, PARENT_ORGANISATION_ID);
        } finally {
            switchToUserOfAnotherOrganisation(username);
        }
    }

    // The organisations that own the enrolment mappings of the pair which the current user can see.
    private List<Long> organisationsMappingEnrolmentAsSeenHere(String subjectTypeUuid, String programUuid) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        return new TransactionTemplate(transactionManager).execute(status -> formMappingRepository.getAllProgramEnrolmentFormMapping(subjectType).stream()
                .filter(formMapping -> programUuid.equals(formMapping.getProgramUuid()))
                .map(FormMapping::getOrganisationId)
                .collect(Collectors.toList()));
    }

    // A dashboard saved before the preset existed, in the shape the default dashboard has: its Subject Type filter's config is
    // {"type": "SubjectType"} and nothing else. It goes in through the repository, because a web save, before this change as
    // after it, also writes subjectTypeUUID and widget, as nulls.
    private Dashboard storeDashboardFromBeforeThePreset(String filterUuid) {
        DashboardFilter filter = new DashboardFilter();
        filter.setUuid(filterUuid);
        filter.setName("Screening type");
        filter.setFilterConfig(new JsonObject().with("type", "SubjectType"));
        Dashboard dashboard = new Dashboard();
        dashboard.setUuid(UUID.randomUUID().toString());
        dashboard.setName("Dashboard from before the preset");
        dashboard.addUpdateFilter(filter);
        return dashboardRepository.save(dashboard);
    }

    // A dashboard with no sections and one filter. It is built from plain JSON nodes, with the keys written out here
    // instead of taken from the request classes, so a wrong property name on the server cannot go unseen. A preset
    // that is null is left out of the request, and one that is "" is sent as "".
    private String dashboardJson(String dashboardName, String filterType, String presetSubjectTypeUuid, String presetProgramUuid) throws Exception {
        return dashboardJson(dashboardName, "Screening type", filterType, presetSubjectTypeUuid, presetProgramUuid);
    }

    private String dashboardJson(String dashboardName, String filterName, String filterType, String presetSubjectTypeUuid, String presetProgramUuid) throws Exception {
        return dashboardJsonWithFilters(dashboardName, null, filterJson(filterName, filterType, presetSubjectTypeUuid, presetProgramUuid));
    }

    // A dashboard with no sections and the filters given, in that order. A description that is null is left out of the request.
    private String dashboardJsonWithFilters(String dashboardName, String description, ObjectNode... filters) throws Exception {
        ObjectNode dashboard = mapper.createObjectNode().put("name", dashboardName);
        if (description != null) {
            dashboard.put("description", description);
        }
        dashboard.putArray("sections");
        ArrayNode filterArray = dashboard.putArray("filters");
        for (ObjectNode filter : filters) {
            filterArray.add(filter);
        }
        return mapper.writeValueAsString(dashboard);
    }

    // One filter of a dashboard request, with its presets written the same way as in dashboardJson.
    private ObjectNode filterJson(String filterName, String filterType, String presetSubjectTypeUuid, String presetProgramUuid) {
        ObjectNode filterConfig = mapper.createObjectNode().put("type", filterType);
        if (presetSubjectTypeUuid != null) {
            filterConfig.put("presetSubjectTypeUUID", presetSubjectTypeUuid);
        }
        if (presetProgramUuid != null) {
            filterConfig.put("presetProgramUUID", presetProgramUuid);
        }
        ObjectNode filter = mapper.createObjectNode().put("name", filterName);
        filter.set("filterConfig", filterConfig);
        return filter;
    }

    // Saves a dashboard as the current user, which has to succeed, and returns the dashboard the server answered with.
    // Qualified because the base class has its own post(path, json), which hides the static import.
    private JsonNode postDashboard(String json) throws Exception {
        String saved = mockMvc.perform(MockMvcRequestBuilders.post("/web/dashboard").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(saved);
    }

    // Saves a dashboard as the current user, which has to be refused: 400, and the body is returned so the reason can be read.
    private String postDashboardRefused(String json) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/web/dashboard").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
    }

    // Edits a dashboard as the current user, which has to succeed, and returns the dashboard the server answered with.
    private JsonNode putDashboard(long dashboardId, JsonNode edit) throws Exception {
        String saved = mockMvc.perform(put("/web/dashboard/" + dashboardId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(saved);
    }

    // Edits a dashboard as the current user, which has to be refused: 400, and the body is returned so the reason can be read.
    private String putDashboardRefused(long dashboardId, JsonNode edit) throws Exception {
        return mockMvc.perform(put("/web/dashboard/" + dashboardId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(edit)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
    }

    // A new dashboard that is refused is refused whole: 400 with the reason in the body, and no dashboard is left under its name.
    private void assertNewDashboardIsRefused(String dashboardName, String json, String reason) throws Exception {
        assertThat(postDashboardRefused(json)).as("the answer to the refused save of " + dashboardName).contains(reason);
        assertNoDashboardNamed(dashboardName);
    }

    private void assertNoDashboardNamed(String dashboardName) throws Exception {
        assertThat(namesOfListedDashboards()).as("dashboards listed after the refused save").doesNotContain(dashboardName);
    }

    // The dashboards the web app lists for the current organisation.
    private List<String> namesOfListedDashboards() throws Exception {
        String body = mockMvc.perform(get("/web/dashboard").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = new ArrayList<>();
        mapper.readTree(body).forEach(dashboard -> names.add(dashboard.path("name").textValue()));
        return names;
    }

    // The filter sync, read from the beginning of time.
    private JsonNode filterFromTheSync(String filterUuid) throws Exception {
        return filterFromTheSync(filterUuid, new DateTime(2000, 1, 1, 0, 0, DateTimeZone.UTC));
    }

    // The filter sync as a phone asks for it, from the time given: the filters changed from then on are in it. The filters are
    // under _embedded.dashboardFilter.
    private JsonNode filterFromTheSync(String filterUuid, DateTime syncedFrom) throws Exception {
        String body = mockMvc.perform(get("/v2/dashboardFilter/search/lastModified")
                        .param("lastModifiedDateTime", syncedFrom.withZone(DateTimeZone.UTC).toString())
                        .param("now", DateTime.now(DateTimeZone.UTC).plusMinutes(1).toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode filter : mapper.readTree(body).path("_embedded").path("dashboardFilter")) {
            if (filterUuid.equals(filter.path("uuid").textValue())) {
                return filter;
            }
        }
        throw new AssertionError("the saved filter " + filterUuid + " is not under _embedded.dashboardFilter in the filter sync from " + syncedFrom + ": " + body);
    }

    // The last modified time a sync entry carries, as the phone reads it.
    private static DateTime lastModifiedOf(JsonNode syncEntry) {
        JsonNode lastModified = syncEntry.path("lastModifiedDateTime");
        assertThat(lastModified.isTextual()).as("lastModifiedDateTime of the sync entry: " + syncEntry).isTrue();
        return new DateTime(lastModified.textValue());
    }

    // The filters of a dashboard as the web app reads it come from a set, so their order is not fixed. They are found by name.
    private ObjectNode filterNamed(JsonNode dashboard, String filterName) {
        for (JsonNode filter : dashboard.path("filters")) {
            if (filterName.equals(filter.path("name").textValue())) {
                return (ObjectNode) filter;
            }
        }
        throw new AssertionError("no filter named " + filterName + " in the dashboard: " + dashboard);
    }

    private List<String> namesOfFilters(JsonNode dashboard) {
        List<String> names = new ArrayList<>();
        dashboard.path("filters").forEach(filter -> names.add(filter.path("name").textValue()));
        return names;
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

    // For a filter in the sync: neither preset key is there. Soft, so one failure names both. The type is asserted too, because a
    // filter config that is not there at all would also have neither key.
    private void assertCarriesNoPreset(JsonNode syncedFilterConfig, String filterType, String where) {
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(syncedFilterConfig.path("type").textValue()).as("type of " + where).isEqualTo(filterType);
            softly.assertThat(syncedFilterConfig.has("presetSubjectTypeUUID")).as(where + " has presetSubjectTypeUUID: " + syncedFilterConfig).isFalse();
            softly.assertThat(syncedFilterConfig.has("presetProgramUUID")).as(where + " has presetProgramUUID: " + syncedFilterConfig).isFalse();
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
