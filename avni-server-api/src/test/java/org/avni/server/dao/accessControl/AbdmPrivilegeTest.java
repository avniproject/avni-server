package org.avni.server.dao.accessControl;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupPrivilegeRepository;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.PrivilegeEntityType;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.access.TestGroupPrivilegeBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.service.OrganisationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestGroupService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.abdm.AbdmAdminController;
import org.avni.server.web.request.GroupPrivilegeBundleContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

// F10: sharing records (HIP) and reading received ones (HIU) are privileges of their own, added by V1_421. An organisation
// with both ABDM switches on grants them on the User Groups screen; an account administrator is not passed them without a
// grant, and no group has them until an organisation grants them.
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class AbdmPrivilegeTest extends AbstractControllerIntegrationTest {
    private static final List<PrivilegeType> ABDM = List.of(PrivilegeType.ShareAbdmRecords, PrivilegeType.ViewAbdmRecords);

    @Autowired
    private PrivilegeRepository privilegeRepository;
    @Autowired
    private AccessControlService accessControlService;
    @Autowired
    private GroupPrivilegeService groupPrivilegeService;
    @Autowired
    private GroupPrivilegeRepository groupPrivilegeRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestGroupService testGroupService;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private AbdmAdminController abdmAdminController;

    @Test
    public void bothPrivilegesExistUnderAbdm() {
        for (PrivilegeType type : ABDM) {
            Privilege privilege = privilegeRepository.findByType(type);
            assertNotNull(String.format("No privilege row for %s - is the V1_421 migration present?", type), privilege);
            assertEquals(PrivilegeEntityType.Abdm, privilege.getEntityType());
            assertFalse(privilege.isVoided());
        }
    }

    // The organisation-settings privilege is on the account administrator's list; these are not.
    @Test
    public void neitherIsOnTheListAnAccountAdministratorPassesWithoutAGrant() {
        assertTrue(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditOrganisationConfiguration));
        for (PrivilegeType type : ABDM) assertFalse(type.name(), privilegeRepository.isAllowedForAdmin(type));
        assertFalse(privilegeRepository.isAnyOfSpecificAllowedForAdmin(ABDM));
    }

    // Reads the script, as ApprovalRejectionPrivilegeTest does: tear-down empties group_privilege, so a count proves nothing.
    @Test
    public void theMigrationGrantsThemToNobody() throws IOException {
        String migration = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V1_421__AbdmPrivilegesAndRecordReads.sql").getInputStream(), StandardCharsets.UTF_8);
        String statements = Arrays.stream(migration.split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .toLowerCase();

        assertTrue(statements.contains("insert into privilege"));
        assertFalse(statements.contains("group_privilege"));
    }

    // An organisation that has not switched ABDM on is not offered its privileges; the others are offered as before.
    @Test
    public void theUserGroupsScreenLeavesThemOutUnlessBothSwitchesAreOn() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivscreenoff");
        setUser(organisation.getUser());

        for (boolean[] abhaAndSharing : new boolean[][]{{false, false}, {true, false}, {false, true}}) {
            switches(organisation, abhaAndSharing[0], abhaAndSharing[1]);
            List<PrivilegeType> offered = offered(organisation);

            String state = Arrays.toString(abhaAndSharing);
            assertTrue(state, offered.contains(PrivilegeType.EditOrganisationConfiguration));
            for (PrivilegeType type : ABDM) assertFalse(state + " " + type, offered.contains(type));
        }
    }

    @Test
    public void theUserGroupsScreenOffersThemOnceBothSwitchesAreOn() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivscreenon");
        setUser(organisation.getUser());
        switches(organisation, true, true);

        List<PrivilegeType> offered = offered(organisation);

        assertTrue(offered.toString(), offered.containsAll(ABDM));
    }

    // Switching ABDM off takes the privileges off the screen, not away from a group already granted them.
    @Test
    public void aGrantAlreadyStoredStillPassesWithTheSwitchesOff() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivkept");
        setUser(organisation.getUser());
        User user = organisation.getUser();
        switches(organisation, true, true);
        grant(organisation.getGroup(), PrivilegeType.ShareAbdmRecords);

        switches(organisation, false, false);

        assertFalse(offered(organisation).contains(PrivilegeType.ShareAbdmRecords));
        accessControlService.checkPrivilege(user, PrivilegeType.ShareAbdmRecords);
    }

    // The ABDM page's status opens to a user who may edit the organisation's settings, share records or view them.
    @Test
    public void theStatusOpensToAUserHoldingOnlyTheOrganisationSettingsPrivilege() {
        assertTheStatusOpensWith(PrivilegeType.EditOrganisationConfiguration, "abdmstatusedit");
    }

    @Test
    public void theStatusOpensToAUserHoldingOnlyTheSharePrivilege() {
        assertTheStatusOpensWith(PrivilegeType.ShareAbdmRecords, "abdmstatusshare");
    }

    @Test
    public void theStatusOpensToAUserHoldingOnlyTheViewPrivilege() {
        assertTheStatusOpensWith(PrivilegeType.ViewAbdmRecords, "abdmstatusview");
    }

    @Test
    public void theStatusIsRefusedToAUserHoldingNoneOfTheThree() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmstatusnone");
        setUser(organisation.getUser());
        grant(organisation.getGroup(), PrivilegeType.EditUserGroup);

        assertThrows(AvniAccessException.class, () -> abdmAdminController.status());
    }

    // A user whose group may edit the organisation's settings is refused both until each is granted to the group.
    @Test
    public void aUserHasEachOnlyOnceTheirGroupIsGrantedIt() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivgrant");
        setUser(organisation.getUser());
        User user = organisation.getUser();
        grant(organisation.getGroup(), PrivilegeType.EditOrganisationConfiguration);

        accessControlService.checkPrivilege(user, PrivilegeType.EditOrganisationConfiguration);
        assertThrows(AvniAccessException.class, () -> accessControlService.checkPrivilege(user, PrivilegeType.ShareAbdmRecords));
        assertThrows(AvniAccessException.class, () -> accessControlService.checkPrivilege(user, PrivilegeType.ViewAbdmRecords));

        grant(organisation.getGroup(), PrivilegeType.ShareAbdmRecords);

        accessControlService.checkPrivilege(user, PrivilegeType.ShareAbdmRecords);
        assertThrows(AvniAccessException.class, () -> accessControlService.checkPrivilege(user, PrivilegeType.ViewAbdmRecords));

        grant(organisation.getGroup(), PrivilegeType.ViewAbdmRecords);

        accessControlService.checkPrivilege(user, PrivilegeType.ViewAbdmRecords);
    }

    @Test
    public void anAccountAdministratorIsRefusedBothWithoutAGrant() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivadmin");
        setUser(organisation.getUser());
        User accountAdmin = organisation.getUser2();
        userGroupRepository.deleteAll(userGroupRepository.findByUserAndGroupHasAllPrivilegesTrueAndIsVoidedFalse(accountAdmin));
        accountAdmin.setAdmin(true);

        accessControlService.checkPrivilege(accountAdmin, PrivilegeType.EditOrganisationConfiguration);
        for (PrivilegeType type : ABDM) {
            assertThrows(type.name(), AvniAccessException.class, () -> accessControlService.checkPrivilege(accountAdmin, type));
        }
    }

    // The platform's rule for every privilege: a member of a group with all privileges (an organisation's Administrators)
    // has these too, without a grant.
    @Test
    public void aMemberOfAGroupWithAllPrivilegesHasBoth() {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivall");
        setUser(organisation.getUser());
        Group administrators = groupRepository.findByNameAndOrganisationId(Group.Administrators, organisation.getOrganisationId());
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(administrators).withUser(organisation.getUser2()).build());

        for (PrivilegeType type : ABDM) accessControlService.checkPrivilege(organisation.getUser2(), type);
    }

    // A grant goes out in the bundle's groupPrivilege.json by privilege type, and comes back in on import.
    @Test
    public void aGrantTravelsInTheBundle() throws IOException {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation("abdmprivbundle");
        setUser(organisation.getUser());
        User user = organisation.getUser();
        grant(organisation.getGroup(), PrivilegeType.ShareAbdmRecords);

        GroupPrivilegeBundleContract[] exported = exportedGroupPrivileges();
        GroupPrivilegeBundleContract share = Arrays.stream(exported).filter(c -> c.getPrivilegeType() == PrivilegeType.ShareAbdmRecords)
                .findFirst().orElse(null);
        assertNotNull("groupPrivilege.json carries no ShareAbdmRecords grant", share);
        assertEquals(organisation.getGroup().getUuid(), share.getGroupUUID());
        assertTrue(share.isAllow());

        groupPrivilegeRepository.deleteAll(groupPrivilegeRepository.findAll().stream()
                .filter(gp -> gp.getPrivilege().getType() == PrivilegeType.ShareAbdmRecords).collect(Collectors.toList()));
        groupPrivilegeRepository.flush();
        assertThrows(AvniAccessException.class, () -> accessControlService.checkPrivilege(user, PrivilegeType.ShareAbdmRecords));

        groupPrivilegeService.savePrivilegesFromBundle(exported, organisationRepository.findOne(organisation.getOrganisationId()));
        groupPrivilegeRepository.flush();

        accessControlService.checkPrivilege(user, PrivilegeType.ShareAbdmRecords);
    }

    private void assertTheStatusOpensWith(PrivilegeType type, String organisationName) {
        TestDataSetupService.TestOrganisationData organisation = testDataSetupService.setupOrganisation(organisationName);
        setUser(organisation.getUser());
        grant(organisation.getGroup(), type);

        Map<String, Object> status = abdmAdminController.status();

        assertEquals(false, status.get("recordSharingOn"));
    }

    private List<PrivilegeType> offered(TestDataSetupService.TestOrganisationData organisation) {
        return groupPrivilegeService.getAllGroupPrivileges(organisation.getGroup().getId()).stream()
                .map(gp -> gp.getPrivilege().getType()).collect(Collectors.toList());
    }

    private void switches(TestDataSetupService.TestOrganisationData organisation, boolean abha, boolean recordSharing) {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisation.getOrganisationId());
        config.setSettings(new JsonObject(config.getSettings())
                .with(OrganisationConfigSettingKey.enableAbha.name(), abha)
                .with(OrganisationConfigSettingKey.enableAbdmRecordSharing.name(), recordSharing));
        organisationConfigRepository.saveAndFlush(config);
    }

    private void grant(Group group, PrivilegeType type) {
        GroupPrivilege groupPrivilege = new TestGroupPrivilegeBuilder().withDefaultValuesForNewEntity().build();
        testGroupService.updateGroup(group, Map.of(groupPrivilege, type));
        groupPrivilegeRepository.flush();
    }

    private GroupPrivilegeBundleContract[] exportedGroupPrivileges() throws IOException {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zip)) {
            organisationService.addGroupPrivilegeJson(zos);
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))) {
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                if (entry.getName().equals("groupPrivilege.json")) {
                    return ObjectMapperSingleton.getObjectMapper().readValue(zis.readAllBytes(), GroupPrivilegeBundleContract[].class);
                }
            }
        }
        throw new AssertionError("the bundle has no groupPrivilege.json");
    }
}
