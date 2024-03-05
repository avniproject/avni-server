package org.avni.server.service;

import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivileges;
import org.avni.server.domain.factory.*;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.web.request.UserSubjectAssignmentContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserSubjectAssignmentServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    @Mock
    private GroupSubjectRepository groupSubjectRepository;
    @Mock
    private GroupPrivilegeService groupPrivilegeService;
    @Mock
    private ChecklistService checklistService;
    @Mock
    private ChecklistItemService checklistItemService;
    @Mock
    private IndividualRelationshipService individualRelationshipService;
    @Captor
    ArgumentCaptor<UserSubjectAssignment> userSubjectAssignmentCaptor;
    @Mock
    private AvniMetaDataRuleService avniMetaDataRuleService;
    @Mock
    private AddressLevelService addressLevelService;

    private UserSubjectAssignmentService userSubjectAssignmentService;
    private UserSubjectAssignmentContract userSubjectAssignmentContract;
    private Organisation organisation;
    private AddressLevelType addressLevelType;
    private AddressLevel addressLevelWithinCatchment;
    private AddressLevel addressLevelOutsideCatchment;
    private Catchment catchment;


    @Before
    public void initialize() {
        initMocks(this);

        userSubjectAssignmentService = new UserSubjectAssignmentService(userSubjectAssignmentRepository, userRepository, null, null, null, null, null, individualRepository, checklistService, checklistItemService, individualRelationshipService, groupSubjectRepository, groupPrivilegeService, addressLevelService);
        userSubjectAssignmentContract = new UserSubjectAssignmentContract();
        userSubjectAssignmentContract.setSubjectIds(Collections.singletonList(1l));
        userSubjectAssignmentContract.setUserId(1l);
        organisation = new TestOrganisationBuilder().setId(1l).withMandatoryFields().build();
        addressLevelType = new AddressLevelTypeBuilder().withDefaultValuesForNewEntity().build();
        addressLevelWithinCatchment = new AddressLevelBuilder().id(1l).withDefaultValuesForNewEntity().type(addressLevelType).build();
        addressLevelOutsideCatchment = new AddressLevelBuilder().id(2l).withDefaultValuesForNewEntity().type(addressLevelType).build();
        catchment = new TestCatchmentBuilder().withDefaultValuesForNewEntity().withAddressLevels(addressLevelWithinCatchment).build();
    }

    @Test
    public void assigningSubjectShouldAssignMemberSubjects() throws ValidationException {

        Individual group = new SubjectBuilder().setId(1).withLocation(addressLevelWithinCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(true).build()).build();
        when(individualRepository.findAllById(any())).thenReturn(Collections.singletonList(group));
        when(userRepository.findOne(1l)).thenReturn(new UserBuilder().setId(1).withCatchment(catchment).withDefaultValuesForNewEntity().userName("user1@example").organisationId(organisation.getId()).build());
        when(groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(group)).thenReturn(Collections.singletonList(new TestGroupSubjectBuilder().setId(1l).withGroup(group)
                .withMember(new SubjectBuilder().setId(2).withLocation(addressLevelWithinCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(false).build()).setId(2).build()).build()));
        when(groupPrivilegeService.getGroupPrivileges()).thenReturn(new GroupPrivileges(false));
        when(addressLevelService.getAllRegistrationAddressIdsBySubjectType(any(), any())).thenReturn(catchment.getAddressLevels().stream().map(AddressLevel::getId).collect(Collectors.toList()));


        userSubjectAssignmentService.assignSubjects(userSubjectAssignmentContract);
        verify(userSubjectAssignmentRepository, times(2)).save(userSubjectAssignmentCaptor.capture());
        List<UserSubjectAssignment> usa = userSubjectAssignmentCaptor.getAllValues();
        assertEquals(usa.get(0).getUser().getId().longValue(), 1l);
        assertEquals(usa.get(1).getUser().getId().longValue(), 1l);
    }

    @Test(expected = ValidationException.class)
    public void assigningSubjectOutsideUserCatchmentShouldFail() throws ValidationException {

        Individual group = new SubjectBuilder().setId(1).withLocation(addressLevelOutsideCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(true).build()).build();
        when(individualRepository.findAllById(any())).thenReturn(Collections.singletonList(group));
        when(userRepository.findOne(1l)).thenReturn(new UserBuilder().setId(1).withCatchment(catchment).withDefaultValuesForNewEntity().userName("user1@example").organisationId(organisation.getId()).build());
        when(groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(group)).thenReturn(Collections.singletonList(new TestGroupSubjectBuilder().setId(1l).withGroup(group)
                .withMember(new SubjectBuilder().setId(2).withLocation(addressLevelWithinCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(false).build()).setId(2).build()).build()));
        when(groupPrivilegeService.getGroupPrivileges()).thenReturn(new GroupPrivileges(false));
        when(addressLevelService.getAllRegistrationAddressIdsBySubjectType(any(), any())).thenReturn(catchment.getAddressLevels().stream().map(AddressLevel::getId).collect(Collectors.toList()));

        userSubjectAssignmentService.assignSubjects(userSubjectAssignmentContract);
    }

    @Test
    public void assigningGrpSubjectInsideCatchmentWithMemberSubjectOutsideUserCatchmentShouldSucceed() throws ValidationException {

        Individual group = new SubjectBuilder().setId(1).withLocation(addressLevelWithinCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(true).build()).build();
        when(individualRepository.findAllById(any())).thenReturn(Collections.singletonList(group));
        when(userRepository.findOne(1l)).thenReturn(new UserBuilder().setId(1).withCatchment(catchment).withDefaultValuesForNewEntity().userName("user1@example").organisationId(organisation.getId()).build());
        when(groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(group)).thenReturn(Collections.singletonList(new TestGroupSubjectBuilder().setId(1l).withGroup(group)
                .withMember(new SubjectBuilder().setId(2).withLocation(addressLevelOutsideCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(false).build()).setId(2).build()).build()));
        when(groupPrivilegeService.getGroupPrivileges()).thenReturn(new GroupPrivileges(false));
        when(addressLevelService.getAllRegistrationAddressIdsBySubjectType(any(), any())).thenReturn(catchment.getAddressLevels().stream().map(AddressLevel::getId).collect(Collectors.toList()));

        userSubjectAssignmentService.assignSubjects(userSubjectAssignmentContract);
        verify(userSubjectAssignmentRepository, times(2)).save(userSubjectAssignmentCaptor.capture());
        List<UserSubjectAssignment> usa = userSubjectAssignmentCaptor.getAllValues();
        assertEquals(usa.get(0).getUser().getId().longValue(), 1l);
        assertEquals(usa.get(1).getUser().getId().longValue(), 1l);
    }

    @Test
    public void assigningVoidedGrpSubjectInsideCatchmentWithMemberSubjectOutsideUserCatchment() throws ValidationException {
        userSubjectAssignmentContract.setVoided(true);

        Individual group = new SubjectBuilder().setId(1).withLocation(addressLevelWithinCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(true).build()).build();
        when(individualRepository.findAllById(any())).thenReturn(Collections.singletonList(group));
        when(userRepository.findOne(1l)).thenReturn(new UserBuilder().setId(1).withCatchment(catchment).withDefaultValuesForNewEntity().userName("user1@example").organisationId(organisation.getId()).build());
        when(groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(group)).thenReturn(Collections.singletonList(new TestGroupSubjectBuilder().setId(1l).withGroup(group)
                .withMember(new SubjectBuilder().setId(2).withLocation(addressLevelOutsideCatchment).withSubjectType(new SubjectTypeBuilder().setGroup(false).build()).setId(2).build()).build()));
        when(groupPrivilegeService.getGroupPrivileges()).thenReturn(new GroupPrivileges(false));
        when(addressLevelService.getAllRegistrationAddressIdsBySubjectType(any(), any())).thenReturn(catchment.getAddressLevels().stream().map(AddressLevel::getId).collect(Collectors.toList()));

        userSubjectAssignmentService.assignSubjects(userSubjectAssignmentContract);
        verify(userSubjectAssignmentRepository, times(1)).save(userSubjectAssignmentCaptor.capture());
        List<UserSubjectAssignment> usa = userSubjectAssignmentCaptor.getAllValues();
        assertEquals(usa.get(0).getUser().getId().longValue(), 1l);
    }
}
