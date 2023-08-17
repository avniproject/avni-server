package org.avni.server.web;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.access.TestGroupBuilder;
import org.avni.server.domain.factory.access.TestUserGroupBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.factory.txn.TestUserSubjectAssignmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestCatchmentService;
import org.avni.server.service.builder.TestLocationService;
import org.avni.server.service.builder.TestOrganisationService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import wiremock.org.checkerframework.checker.units.qual.A;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SyncControllerTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SyncController syncController;
    @Autowired
    private IndividualController individualController;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CatchmentRepository catchmentRepository;
    @Autowired
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private IndividualRepository subjectRepository;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private GroupSubjectRepository groupSubjectRepository;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestOrganisationService testOrganisationService;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
    @Autowired
    private TestCatchmentService testCatchmentService;
    @Autowired
    private TestLocationService testLocationService;

    @Test
    public void getSyncDetailsOfSubjectsAndGroupSubjectsBasedOnDirectAssignment() {
        User user = new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(userRepository.getDefaultSuperAdmin()).build();
        Organisation organisation = new TestOrganisationBuilder().withMandatoryFields().withAccount(accountRepository.getDefaultAccount()).build();
        testOrganisationService.createOrganisation(organisation, user);
        userRepository.save(new UserBuilder(user).withAuditUser(user).build());
        setUser(user.getUsername());

        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        Group group = groupRepository.save(new TestGroupBuilder().withMandatoryFieldsForNewEntity().withAllPrivileges(true).build());
        userGroupRepository.save(new TestUserGroupBuilder().withGroup(group).withUser(user).build());

        AddressLevelType addressLevelType = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build());
        AddressLevel addressLevel1 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        AddressLevel addressLevel2 = testLocationService.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        Catchment catchment = new TestCatchmentBuilder().withDefaultValuesForNewEntity().build();
        testCatchmentService.createCatchment(catchment, addressLevel1);

        user = userRepository.save(new UserBuilder(user).withCatchment(catchment).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        setUser(user.getUsername());

        SubjectType st1 = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().withMandatoryFieldsForNewEntity().withType(Subject.Individual).build(),
                new TestFormBuilder().withDefaultFieldsForNewEntity().build());
        SubjectType st2 = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().withMandatoryFieldsForNewEntity().withType(Subject.Group).build(),
                new TestFormBuilder().withDefaultFieldsForNewEntity().build());

        GroupRole groupRole = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withMemberSubjectType(st1).withGroupSubjectType(st2).build());

        Individual syncsDueToLocation1 = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel1).build());
        Individual syncsDueToAssignmentOfItsGroup = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel2).build());
        Individual syncsDueToLocation2 = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel1).build());
        Individual doesntSync1 = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel2).build());

        Individual syncsDueToLocation3 = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel1).build());
        Individual syncsDueToLocation4 = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel1).build());
        Individual syncsDueToAssignment = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel2).build());
        Individual doesntSync2 = subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel2).build());

        GroupSubject groupSubject = groupSubjectRepository.save(new TestGroupSubjectBuilder().withGroup(syncsDueToLocation3).withMember(syncsDueToLocation1).withGroupRole(groupRole).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(syncsDueToAssignment).withUser(user).build());

        List<EntitySyncStatusContract> contracts = SyncEntityName.getEntitiesWithoutSubEntity().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
        List syncDetails = ((JsonObject) response.getBody()).getList("syncDetails");

        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st1.getUuid())));
        assertTrue(syncDetails.contains(EntitySyncStatusContract.createForComparison(SyncEntityName.Individual.name(), st2.getUuid())));
        assertEquals(3, getNumberOfSubjects(st1));
        assertEquals(3, getNumberOfSubjects(st2));
    }

    private long getNumberOfSubjects(SubjectType st1) {
        SlicedResources<Resource<Individual>> individuals = individualController.getIndividualsByOperatingIndividualScopeAsSlice(DateTime.now().minusDays(1), DateTime.now(), st1.getUuid(), PageRequest.of(0, 10));
        return individuals.getContent().size();
    }

    public static String readFile(String path) {
        try {
            return new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream())).lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void tearDown() {
        System.out.println("");
//        try {
//            jdbcTemplate.execute(readFile("tear-down.sql"));
//        } catch (DataAccessException e) {
//            e.printStackTrace();
//        }
    }
}
