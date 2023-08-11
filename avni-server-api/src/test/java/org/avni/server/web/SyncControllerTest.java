package org.avni.server.web;

import org.avni.server.application.Subject;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupRoleBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.factory.txn.TestUserSubjectAssignmentBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.web.request.EntitySyncStatusContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional
public class SyncControllerTest extends AbstractControllerIntegrationTest {
    @Autowired
    private SyncController syncController;
    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private OrganisationConfigRepository organisationConfigRepository;
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
    private SubjectTypeRepository subjectTypeRepository;
    @Autowired
    private IndividualRepository subjectRepository;
    @Autowired
    private GroupRoleRepository groupRoleRepository;
    @Autowired
    private GroupSubjectRepository groupSubjectRepository;
    @Autowired
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;

    @Test
    public void syncSubjectsAndGroupSubjectsBasedOnDirectAssignment() {
        Organisation organisation = organisationRepository.save(new TestOrganisationBuilder().withMandatoryFields().withAccount(accountRepository.getDefaultAccount()).build());
        User user = userRepository.save(new UserBuilder().withDefaultValuesForNewEntity().userName("user@example").withAuditUser(userRepository.getDefaultSuperAdmin()).organisationId(organisation.getId()).build());
        setUser(user.getUsername());

        AddressLevelType addressLevelType = addressLevelTypeRepository.save(new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build());
        AddressLevel addressLevel1 = locationRepository.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        AddressLevel addressLevel2 = locationRepository.save(new AddressLevelBuilder().withDefaultValuesForNewEntity().type(addressLevelType).build());
        Catchment catchment = catchmentRepository.save(new TestCatchmentBuilder().withDefaultValuesForNewEntity().withAddressLevels(addressLevel1).build());
        user = userRepository.save(new UserBuilder(user).withCatchment(catchment).withOperatingIndividualScope(OperatingIndividualScope.ByCatchment).build());
        organisationConfigRepository.save(new TestOrganisationConfigBuilder().withMandatoryFields().withOrganisationId(organisation.getId()).build());

        SubjectType st1 = subjectTypeRepository.save(new SubjectTypeBuilder().withMandatoryFieldsForNewEntity().withType(Subject.Individual).build());
        Individual syncsDueToLocation1 = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel1).build()));
        Individual syncsDueToAssignmentOfItsGroup = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel2).build()));
        Individual syncsDueToLocation2 = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel1).build()));
        Individual doesntSync1 = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st1).withLocation(addressLevel2).build()));
        SubjectType st2 = subjectTypeRepository.save(new SubjectTypeBuilder().withMandatoryFieldsForNewEntity().withType(Subject.Group).build());
        Individual syncsDueToLocation3 = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel1).build()));
        Individual syncsDueToLocation4 = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel1).build()));
        Individual syncsDueToAssignment = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel2).build()));
        Individual doesntSync2 = subjectRepository.save(subjectRepository.save(new SubjectBuilder().withMandatoryFieldsForNewEntity().withSubjectType(st2).withLocation(addressLevel2).build()));

        GroupRole groupRole = groupRoleRepository.save(new TestGroupRoleBuilder().withMandatoryFieldsForNewEntity().withMemberSubjectType(st1).withGroupSubjectType(st2).build());
        GroupSubject groupSubject = groupSubjectRepository.save(new TestGroupSubjectBuilder().withUuid(UUID.randomUUID().toString()).withGroup(syncsDueToLocation3).withMember(syncsDueToLocation1).withGroupRole(groupRole).build());

        userSubjectAssignmentRepository.save(new TestUserSubjectAssignmentBuilder().withMandatoryFieldsForNewEntity().withSubject(syncsDueToAssignment).withUser(user).build());

        List<EntitySyncStatusContract> contracts = SyncEntityName.getEntitiesWithoutSubEntity().stream().map(EntitySyncStatusContract::createForEntityWithoutSubType).collect(Collectors.toList());
        ResponseEntity<?> response = syncController.getSyncDetailsWithScopeAwareEAS(contracts, false);
    }
}
