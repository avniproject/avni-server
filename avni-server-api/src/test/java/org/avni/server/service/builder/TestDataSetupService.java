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

    public TestOrganisationData setupOrganisation() {
        Group group = new TestGroupBuilder().withMandatoryFieldsForNewEntity().build();
        User user = new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(userRepository.getDefaultSuperAdmin()).build();
        Organisation organisation = new TestOrganisationBuilder().withMandatoryFields().withAccount(accountRepository.getDefaultAccount()).build();
        testOrganisationService.createOrganisation(organisation, user);
        userRepository.save(new UserBuilder(user).withAuditUser(user).build());
        testWebContextService.setUser(user.getUsername());

        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        groupRepository.save(group);
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user).build());
        return new TestOrganisationData(user, group, organisation);
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
        private AddressLevelType addressLevelType;
        private AddressLevel addressLevel1;
        private AddressLevel addressLevel2;
        private Catchment catchment;

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
    }
}
