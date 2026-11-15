package org.avni.server.importer.batch.zip;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupPrivilegeRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.BulkUploadS3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.util.ObjectMapperSingleton;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

// groupPrivilege.json names each grant's privilege by type. A bundle from a server with a privilege this one lacks names a
// type it does not know: that grant is skipped, the rest of the file imports, and the uploader is told in the upload's
// error file, the upload showing as completed with errors.
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class BundleGroupPrivilegeImportTest extends AbstractControllerIntegrationTest {
    private static final String UNKNOWN_TYPE = "ApproveThingsFromALaterRelease";
    private static final String UNKNOWN_PRIVILEGE_UUID = "privilege-uuid-not-on-this-server";

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private GroupPrivilegeRepository groupPrivilegeRepository;
    @Autowired
    private BulkUploadS3Service bulkUploadS3Service;
    private final List<File> errorFiles = new ArrayList<>();

    @Test
    public void aGrantOfAPrivilegeTypeThisServerDoesNotKnowIsSkippedAndTheRestImport() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("bundleprivtype");
        setUser(organisation.getUser());
        List<Map<String, Object>> grants = List.of(
                grant(organisation, "privilegeType", PrivilegeType.EditUserGroup.name()),
                grant(organisation, "privilegeType", UNKNOWN_TYPE),
                grant(organisation, "privilegeType", PrivilegeType.EditOrganisationConfiguration.name()));

        Imported imported = importGrants(organisation, grants);

        assertBothKnownGrantsImported(organisation.getUser());
        assertWarned(imported.logged, UNKNOWN_TYPE);
        assertToldTheUploader(imported, UNKNOWN_TYPE, organisation.getGroup().getUuid());
    }

    // An older bundle names the privilege by its UUID alone; one this server has no privilege for is skipped the same way.
    @Test
    public void aGrantOfAPrivilegeUuidThisServerDoesNotHaveIsSkippedAndTheRestImport() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("bundleprivuuid");
        setUser(organisation.getUser());
        List<Map<String, Object>> grants = List.of(
                grant(organisation, "privilegeType", PrivilegeType.EditUserGroup.name()),
                grant(organisation, "privilegeUUID", UNKNOWN_PRIVILEGE_UUID),
                grant(organisation, "privilegeType", PrivilegeType.EditOrganisationConfiguration.name()));

        Imported imported = importGrants(organisation, grants);

        assertBothKnownGrantsImported(organisation.getUser());
        assertWarned(imported.logged, UNKNOWN_PRIVILEGE_UUID);
        assertToldTheUploader(imported, UNKNOWN_PRIVILEGE_UUID, organisation.getGroup().getUuid());
    }

    // The type is the bundle's text: a line break in it cannot start a line of its own in the log.
    @Test
    public void aTypeWithALineBreakIsLoggedOnOneLine() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("bundleprivcrlf");
        setUser(organisation.getUser());

        Imported imported = importGrants(organisation, List.of(grant(organisation, "privilegeType", "Forged\r\nWARN made up line")));

        List<String> skips = imported.logged.stream().filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains("Forged")).toList();
        assertEquals(skips.toString(), 1, skips.size());
        assertFalse(skips.get(0), skips.get(0).contains("\n") || skips.get(0).contains("\r"));
    }

    // A file that skips nothing leaves the upload as it was: no error file row, nothing counted as skipped.
    @Test
    public void aFileOfKnownPrivilegesTellsTheUploaderNothing() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("bundleprivclean");
        setUser(organisation.getUser());

        Imported imported = importGrants(organisation, List.of(grant(organisation, "privilegeType", PrivilegeType.EditUserGroup.name())));

        assertEquals(0, imported.step.getWriteSkipCount());
        assertFalse(errorFile(imported.step).exists() && errorFile(imported.step).length() > 0);
    }

    private void assertBothKnownGrantsImported(User user) {
        accessControlService.checkPrivilege(user, PrivilegeType.EditUserGroup);
        accessControlService.checkPrivilege(user, PrivilegeType.EditOrganisationConfiguration);
        assertThrows(AvniAccessException.class, () -> accessControlService.checkPrivilege(user, PrivilegeType.EditConcept));
    }

    private static void assertWarned(List<ILoggingEvent> logged, String naming) {
        assertTrue("no warning names " + naming, logged.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(naming)));
    }

    // One row naming the grant in the upload's error file, which the upload offers since it counts as completed with errors.
    private void assertToldTheUploader(Imported imported, String privilege, String groupUuid) throws Exception {
        File errorFile = errorFile(imported.step);
        assertTrue("no error file", errorFile.exists());
        List<String> rows = Files.readAllLines(errorFile.toPath(), StandardCharsets.UTF_8).stream()
                .filter(row -> row.contains("groupPrivilege.json")).toList();
        assertEquals(rows.toString(), 1, rows.size());
        assertTrue(rows.get(0), rows.get(0).contains(privilege) && rows.get(0).contains(groupUuid));
        assertEquals(1, imported.step.getWriteSkipCount());
    }

    private File errorFile(StepExecution step) {
        return bulkUploadS3Service.getLocalErrorFile(step.getJobParameters().getString("uuid"));
    }

    private static Map<String, Object> grant(TestDataSetupService.TestOrganisationData organisation, String privilegeKey, String privilege) {
        Map<String, Object> grant = new LinkedHashMap<>();
        grant.put("uuid", UUID.randomUUID().toString());
        grant.put("groupUUID", organisation.getGroup().getUuid());
        grant.put(privilegeKey, privilege);
        grant.put("allow", true);
        grant.put("voided", false);
        grant.put("notEveryoneGroup", true);
        return grant;
    }

    private record Imported(StepExecution step, List<ILoggingEvent> logged) {
    }

    @After
    public void deleteTheErrorFiles() throws Exception {
        for (File errorFile : errorFiles) Files.deleteIfExists(errorFile.toPath());
    }

    private Imported importGrants(TestDataSetupService.TestOrganisationData organisation, List<Map<String, Object>> grants) throws Exception {
        Logger avni = (Logger) LoggerFactory.getLogger("org.avni.server");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        avni.addAppender(appender);
        StepExecution step;
        try {
            String organisationUuid = organisationRepository.findOne(organisation.getOrganisationId()).getUuid();
            step = BundleFileImport.importFile(applicationContext, organisation.getUser(), organisationUuid, "groupPrivilege.json",
                    ObjectMapperSingleton.getObjectMapper().writeValueAsString(grants));
            groupPrivilegeRepository.flush();
        } finally {
            avni.detachAppender(appender);
        }
        errorFiles.add(errorFile(step));
        return new Imported(step, new ArrayList<>(appender.list));
    }
}
