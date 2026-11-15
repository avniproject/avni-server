package org.avni.server.importer.batch.zip;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupPrivilegeRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.access.TestGroupPrivilegeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.OrganisationConfigController;
import org.avni.server.web.request.OrganisationConfigRequest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

// A bundle carries the exporting organisation's ABDM switches. Uploading it leaves the importing organisation's own as
// they were, off or on, so a UAT bundle cannot switch ABDM on in production; every other setting in it applies.
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class BundleOrganisationConfigImportTest extends AbstractControllerIntegrationTest {
    private static final String ABHA = OrganisationConfigSettingKey.enableAbha.name();
    private static final String SHARING = OrganisationConfigSettingKey.enableAbdmRecordSharing.name();

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private GroupPrivilegeRepository groupPrivilegeRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private OrganisationConfigController organisationConfigController;

    @Test
    public void switchesOnInTheBundleStayAbsentWhereTheOrganisationHasNeither() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = organisationWith("bundleabdmabsent", new JsonObject());

        importOrganisationConfig(organisation, bundleSettings().with(ABHA, true).with(SHARING, true));

        JsonObject settings = settingsOf(organisation);
        assertFalse(settings.toString(), settings.containsKey(ABHA));
        assertFalse(settings.toString(), settings.containsKey(SHARING));
        assertTheRestOfTheBundleApplied(settings);
    }

    @Test
    public void switchesOnInTheBundleStayOffWhereTheOrganisationHasThemOff() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = organisationWith("bundleabdmoff",
                new JsonObject().with(ABHA, false).with(SHARING, false));

        importOrganisationConfig(organisation, bundleSettings().with(ABHA, true).with(SHARING, true));

        JsonObject settings = settingsOf(organisation);
        assertEquals(false, settings.get(ABHA));
        assertEquals(false, settings.get(SHARING));
        assertTheRestOfTheBundleApplied(settings);
    }

    @Test
    public void switchesStayOnWhereTheOrganisationHasThemOnWhateverTheBundleSays() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = organisationWith("bundleabdmon",
                new JsonObject().with(ABHA, true).with(SHARING, true));

        for (JsonObject bundle : List.of(bundleSettings().with(ABHA, true).with(SHARING, true),
                bundleSettings().with(ABHA, false).with(SHARING, false),
                bundleSettings())) {
            importOrganisationConfig(organisation, bundle);

            JsonObject settings = settingsOf(organisation);
            assertEquals(bundle.toString(), true, settings.get(ABHA));
            assertEquals(bundle.toString(), true, settings.get(SHARING));
            assertTheRestOfTheBundleApplied(settings);
        }
    }

    // The organisation's settings screen saves through POST and PUT /organisationConfig; both still switch ABDM on.
    @Test
    public void theSettingsScreenStillSwitchesThemOn() {
        TestDataSetupService.TestOrganisationData organisation = organisationWith("settingsabdmon", new JsonObject());
        setUser(organisation.getUser());
        grant(organisation, PrivilegeType.EditOrganisationConfiguration);

        organisationConfigController.save(request(new JsonObject().with(ABHA, true).with(SHARING, false)));
        organisationConfigRepository.flush();
        assertEquals(true, settingsOf(organisation).get(ABHA));
        assertEquals(false, settingsOf(organisation).get(SHARING));

        organisationConfigController.update(request(new JsonObject().with(SHARING, true)));
        organisationConfigRepository.flush();
        assertEquals(true, settingsOf(organisation).get(ABHA));
        assertEquals(true, settingsOf(organisation).get(SHARING));
    }

    private TestDataSetupService.TestOrganisationData organisationWith(String name, JsonObject settings) {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation(name);
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisation.getOrganisationId());
        config.setSettings(settings.with("languages", List.of("en")));
        organisationConfigRepository.saveAndFlush(config);
        return organisation;
    }

    private static JsonObject bundleSettings() {
        return new JsonObject().with("languages", List.of("en", "hi_IN")).with("enableComments", true);
    }

    private static void assertTheRestOfTheBundleApplied(JsonObject settings) {
        assertEquals(List.of("en", "hi_IN"), settings.get("languages"));
        assertEquals(true, settings.get("enableComments"));
    }

    private void importOrganisationConfig(TestDataSetupService.TestOrganisationData organisation, JsonObject settings) throws Exception {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("uuid", UUID.randomUUID().toString());
        file.put("settings", settings);
        file.put("worklistUpdationRule", null);
        String organisationUuid = organisationRepository.findOne(organisation.getOrganisationId()).getUuid();
        BundleFileImport.importFile(applicationContext, organisation.getUser(), organisationUuid, "organisationConfig.json",
                ObjectMapperSingleton.getObjectMapper().writeValueAsString(file));
        organisationConfigRepository.flush();
    }

    private JsonObject settingsOf(TestDataSetupService.TestOrganisationData organisation) {
        return organisationConfigRepository.findByOrganisationId(organisation.getOrganisationId()).getSettings();
    }

    private static OrganisationConfigRequest request(JsonObject settings) {
        OrganisationConfigRequest request = new OrganisationConfigRequest();
        request.setSettings(settings);
        return request;
    }

    private void grant(TestDataSetupService.TestOrganisationData organisation, PrivilegeType type) {
        GroupPrivilege groupPrivilege = new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build();
        testGroupService.updateGroup(organisation.getGroup(), Map.of(groupPrivilege, type));
        groupPrivilegeRepository.flush();
    }
}
