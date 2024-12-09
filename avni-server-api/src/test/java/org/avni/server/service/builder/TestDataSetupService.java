package org.avni.server.service.builder;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.web.TestWebContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.mail.Address;
import java.util.*;
import java.util.stream.Collectors;

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
    private TestConceptService testConceptService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private OrganisationCategoryRepository organisationCategoryRepository;
    @Autowired
    private OrganisationStatusRepository organisationStatusRepository;

    public TestOrganisationData setupOrganisation(String orgSuffix, String userGroupName) {
        User defaultSuperAdmin = userRepository.getDefaultSuperAdmin();
        testWebContextService.setUser(defaultSuperAdmin);
        User user1 = new UserBuilder().withDefaultValuesForNewEntity().userName(String.format("user@%s", orgSuffix)).withAuditUser(defaultSuperAdmin).build();
        Organisation organisation = new TestOrganisationBuilder()
                .withUsernameSuffix(orgSuffix)
                .setCategory(organisationCategoryRepository.findEntity(1L))
                .withStatus(organisationStatusRepository.findEntity(1L))
                .withMandatoryFields()
                .withAccount(accountRepository.getDefaultAccount()).build();
        testOrganisationService.createOrganisation(organisation, user1);

        testWebContextService.setUser(user1.getUsername());


        Group group = new TestGroupBuilder().withMandatoryFieldsForNewEntity().withName(userGroupName).build();
        User user2 = new UserBuilder().withDefaultValuesForNewEntity().userName(String.format("user2@%s", orgSuffix)).withAuditUser(user1).build();
        testOrganisationService.createUser(organisation, user2);
        userRepository.save(new UserBuilder(user2).withAuditUser(user1).build());
        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        groupRepository.save(group);
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user1).build());
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user2).build());
        TestOrganisationData testOrganisationData = new TestOrganisationData(user1, group, organisation);
        testOrganisationData.setUser2(user2);
        return testOrganisationData;
    }

    public TestOrganisationData setupOrganisation(String orgSuffix) {
        return this.setupOrganisation(orgSuffix, UUID.randomUUID().toString());
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

    public void saveLocationTypes(List<AddressLevelType> lowestToHighestAddressLevelTypes) {
        AddressLevelType lastAddressLevelType = null;
        for (AddressLevelType addressLevelType : lowestToHighestAddressLevelTypes) {
            if (lastAddressLevelType != null)
                addressLevelType.addChildAddressLevelType(lastAddressLevelType);
            lastAddressLevelType = addressLevelTypeRepository.save(addressLevelType);
        }
    }

    public TestSyncAttributeBasedSubjectTypeData setupSubjectTypeWithSyncAttributes() {
        Concept conceptForAttributeBasedSync = testConceptService.createCodedConcept("Concept Name 1", "Answer 1", "Answer 2");
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(
                new SubjectTypeBuilder()
                        .setMandatoryFieldsForNewEntity()
                        .setUuid("subjectTypeWithSyncAttributeBasedSync")
                        .setName("subjectTypeWithSyncAttributeBasedSync")
                        .setSyncRegistrationConcept1Usable(true)
                        .setSyncRegistrationConcept1(conceptForAttributeBasedSync.getUuid()).build());
        return new TestSyncAttributeBasedSubjectTypeData(subjectType, conceptForAttributeBasedSync);
    }

    public static class TestLocationHierarchyData {
        private final Map<String, AddressLevelType> locationTypes;
        private final Map<String, AddressLevel> locations;

        public TestLocationHierarchyData(Map<String, AddressLevelType> locationTypes, Map<String, AddressLevel> locations) {
            this.locationTypes = locationTypes;
            this.locations = locations;
        }

        public Map<String, AddressLevelType> getLocationTypes() {
            return locationTypes;
        }

        public Map<String, AddressLevel> getLocations() {
            return locations;
        }

        public AddressLevelType getLocationType(String name) {
            return locationTypes.get(name);
        }

        public AddressLevel getLocation(String name) {
            return locations.get(name);
        }
    }

    public static class TestSyncAttributeBasedSubjectTypeData {
        private final SubjectType subjectType;
        private final Concept conceptForAttributeBasedSync;

        public TestSyncAttributeBasedSubjectTypeData(SubjectType subjectType, Concept conceptForAttributeBasedSync) {
            this.subjectType = subjectType;
            this.conceptForAttributeBasedSync = conceptForAttributeBasedSync;
        }

        public SubjectType getSubjectType() {
            return subjectType;
        }

        public Concept getSyncConcept() {
            return conceptForAttributeBasedSync;
        }
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
