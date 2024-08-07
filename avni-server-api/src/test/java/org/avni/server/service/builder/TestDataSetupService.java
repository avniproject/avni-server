package org.avni.server.service.builder;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.web.TestWebContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestDataSetupService {
    @Autowired
    private TestOrganisationService testOrganisationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private TestWebContextService testWebContextService;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Autowired
    private TestLocationService testLocationService;
    @Autowired
    private TestCatchmentService testCatchmentService;
    @Autowired
    private OrganisationCategoryRepository organisationCategoryRepository;
    @Autowired
    private OrganisationStatusRepository organisationStatusRepository;

    public TestOrganisationData setupOrganisation(String orgSuffix) {
        Group group = new TestGroupBuilder().withMandatoryFieldsForNewEntity().build();
        User user1 = new UserBuilder().withDefaultValuesForNewEntity().userName(String.format("user@%s", orgSuffix)).withAuditUser(userRepository.getDefaultSuperAdmin()).build();
        User user2 = new UserBuilder().withDefaultValuesForNewEntity().userName(String.format("user2@%s", orgSuffix)).withAuditUser(userRepository.getDefaultSuperAdmin()).build();
        Organisation organisation = new TestOrganisationBuilder()
                .setCategory(organisationCategoryRepository.findEntity(1L))
                .withStatus(organisationStatusRepository.findEntity(1L))
                .withMandatoryFields()
                .withAccount(accountRepository.getDefaultAccount()).build();
        testOrganisationService.createOrganisation(organisation, user1);
        testOrganisationService.createUser(organisation, user2);
        userRepository.save(new UserBuilder(user1).withAuditUser(user1).build());
        userRepository.save(new UserBuilder(user2).withAuditUser(user1).build());
        testWebContextService.setUser(user1.getUsername());

        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        groupRepository.save(group);
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user1).build());
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user2).build());
        TestOrganisationData testOrganisationData = new TestOrganisationData(user1, group, organisation);
        testOrganisationData.setUser2(user2);
        return testOrganisationData;
    }

    public TestOrganisationData setupOrganisation() {
        return this.setupOrganisation("example");
    }

    public TestCatchmentData setupACatchment() {
        AddressLevelType addressLevelType = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build());
        AddressLevel addressLevel1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        AddressLevel addressLevel2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        Catchment catchment = new TestCatchmentBuilder().withDefaultValuesForNewEntity().build();
        testCatchmentService.createCatchment(catchment, addressLevel1);
        return new TestCatchmentData(addressLevelType, addressLevel1, addressLevel2, catchment);
    }

    public static class TestCatchmentData {
        private final AddressLevelType addressLevelType;
        private final AddressLevel addressLevel1;
        private final AddressLevel addressLevel2;
        private final Catchment catchment;

        public TestCatchmentData(AddressLevelType addressLevelType, AddressLevel addressLevel1, AddressLevel addressLevel2, Catchment catchment) {
            this.addressLevelType = addressLevelType;
            this.addressLevel1 = addressLevel1;
            this.addressLevel2 = addressLevel2;
            this.catchment = catchment;
        }

        public AddressLevelType getAddressLevelType() {
            return addressLevelType;
        }

        public AddressLevel getAddressLevel1() {
            return addressLevel1;
        }

        public AddressLevel getAddressLevel2() {
            return addressLevel2;
        }

        public Catchment getCatchment() {
            return catchment;
        }
    }

    public static class TestOrganisationData {
        private final User user;
        private User user2;
        private final Group group;
        private final Organisation organisation;

        public TestOrganisationData(User user, Group group, Organisation organisation) {
            this.user = user;
            this.group = group;
            this.organisation = organisation;
        }

        public User getUser() {
            return user;
        }

        public Group getGroup() {
            return group;
        }

        public long getOrganisationId() {
            return organisation.getId();
        }

        public void setUser2(User user2) {
            this.user2 = user2;
        }

        public User getUser2() {
            return user2;
        }
    }
}
