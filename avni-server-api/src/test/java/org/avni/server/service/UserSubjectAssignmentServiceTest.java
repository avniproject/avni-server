package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserSubjectAssignment;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.accessControl.GroupPrivileges;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.factory.txn.TestGroupSubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.web.request.UserSubjectAssignmentContract;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import javax.persistence.EntityManager;
import java.util.*;

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

    @Test
    public void assigningSubjectShouldAssignMemberSubjects() throws ValidationException {
        initMocks(this);

        UserSubjectAssignmentService userSubjectAssignmentService = new UserSubjectAssignmentService(userSubjectAssignmentRepository, userRepository, null, null, null, null, null, individualRepository, checklistService, checklistItemService, individualRelationshipService, groupSubjectRepository, groupPrivilegeService);
        UserSubjectAssignmentContract userSubjectAssignmentContract = new UserSubjectAssignmentContract();
        userSubjectAssignmentContract.setSubjectIds(Collections.singletonList(1l));
        userSubjectAssignmentContract.setUserId(1l);
        Organisation organisation = new TestOrganisationBuilder().withMandatoryFields().build();

        Individual group = new SubjectBuilder().setId(1).withSubjectType(new SubjectTypeBuilder().setGroup(true).build()).build();
        when(individualRepository.findAllById(any())).thenReturn(Collections.singletonList(group));
        when(groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(group)).thenReturn(Collections.singletonList(new TestGroupSubjectBuilder().setId(1l).withGroup(group).withMember(new SubjectBuilder().setId(2).withSubjectType(new SubjectTypeBuilder().setGroup(false).build()).setId(2).build()).build()));
        when(groupPrivilegeService.getGroupPrivileges(any())).thenReturn(new GroupPrivileges(false));
        when(userRepository.findOne(1l)).thenReturn(new UserBuilder().setId(1).build());

        userSubjectAssignmentService.save(userSubjectAssignmentContract);
        verify(userSubjectAssignmentRepository, times(2)).saveUserSubjectAssignment(userSubjectAssignmentCaptor.capture());
        List<UserSubjectAssignment> usa = userSubjectAssignmentCaptor.getAllValues();
        assertEquals(usa.get(0).getUser().getId().longValue(), 1l);
        assertEquals(usa.get(1).getUser().getId().longValue(), 1l);
    }
}
