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
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.util.ObjectMapperSingleton;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

// groupPrivilege.json names each grant's privilege by type. A bundle from a server with a privilege this one lacks names a
// type it does not know: that grant is skipped with a warning naming it, and the rest of the file imports.
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

    @Test
    public void aGrantOfAPrivilegeTypeThisServerDoesNotKnowIsSkippedAndTheRestImport() throws Exception {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("bundleprivtype");
        setUser(organisation.getUser());
        List<Map<String, Object>> grants = List.of(
                grant(organisation, "privilegeType", PrivilegeType.EditUserGroup.name()),
                grant(organisation, "privilegeType", UNKNOWN_TYPE),
                grant(organisation, "privilegeType", PrivilegeType.EditOrganisationConfiguration.name()));

        List<ILoggingEvent> logged = importLogging(organisation, grants);

        assertBothKnownGrantsImported(organisation.getUser());
        assertWarned(logged, UNKNOWN_TYPE);
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

        List<ILoggingEvent> logged = importLogging(organisation, grants);

        assertBothKnownGrantsImported(organisation.getUser());
        assertWarned(logged, UNKNOWN_PRIVILEGE_UUID);
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

    private List<ILoggingEvent> importLogging(TestDataSetupService.TestOrganisationData organisation, List<Map<String, Object>> grants) throws Exception {
        Logger avni = (Logger) LoggerFactory.getLogger("org.avni.server");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        avni.addAppender(appender);
        try {
            String organisationUuid = organisationRepository.findOne(organisation.getOrganisationId()).getUuid();
            BundleFileImport.importFile(applicationContext, organisation.getUser(), organisationUuid, "groupPrivilege.json",
                    ObjectMapperSingleton.getObjectMapper().writeValueAsString(grants));
            groupPrivilegeRepository.flush();
        } finally {
            avni.detachAppender(appender);
        }
        return new ArrayList<>(appender.list);
    }
}
