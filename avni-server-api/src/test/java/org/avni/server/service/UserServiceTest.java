package org.avni.server.service;

import com.amazonaws.services.cognitoidp.model.AWSCognitoIdentityProviderException;
import org.avni.server.application.Subject;
import org.avni.server.dao.AccountAdminRepository;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.UserSubjectRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.metabase.MetabaseService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class UserServiceTest {
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private IdpServiceFactory idpServiceFactory;
    @Mock
    private UserSubjectRepository userSubjectRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private AccountAdminRepository accountAdminRepository;
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private AccountAdminService accountAdminService;
    @Mock
    private MetabaseService metabaseService;

    @Captor
    ArgumentCaptor<UserGroup> userGroupArgumentCaptor;
    private UserService userService;

    @Before
    public void setup() {
        initMocks(this);

        userService = new UserService(userRepository, groupRepository, userGroupRepository, userSubjectRepository, individualRepository, subjectTypeRepository, idpServiceFactory, accountAdminRepository, organisationRepository, organisationConfigService, accountAdminService, metabaseService);
    }

    @Test
    public void shouldAddToSpecifiedGroupsAndEveryone() {
        long orgId = 1234;
        User user = new UserBuilder().organisationId(orgId).build();
        Group group = new Group();
        Group everyone = new Group();
        String group1 = "Group 1";
        when(groupRepository.findByNameIgnoreCase(group1)).thenReturn(group);
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(everyone);


        userService.addToGroups(user, group1);
        verify(userGroupRepository, times(2)).save(userGroupArgumentCaptor.capture());

        List<UserGroup> allValues = userGroupArgumentCaptor.getAllValues();
        assertEquals(user, allValues.get(0).getUser());
        assertEquals(user, allValues.get(1).getUser());
        assertEquals(everyone, allValues.get(1).getGroup());
    }

    @Test
    public void shouldAddEveryoneIfNoGroupsSpecified() {
        long orgId = 1234;
        User user = new UserBuilder().organisationId(orgId).build();
        Group everyone = new Group();
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(everyone);

        userService.addToGroups(user, null);
        verify(userGroupRepository, times(1)).save(userGroupArgumentCaptor.capture());

        List<UserGroup> allValues = userGroupArgumentCaptor.getAllValues();
        assertEquals(user, allValues.get(0).getUser());
        assertEquals(everyone, allValues.get(0).getGroup());
    }

    @Test
    public void shouldDeduplicateListOfGroupsSpecified() {
        long orgId = 1234;
        User user = new UserBuilder().organisationId(orgId).build();
        Group group1 = new Group();
        Group group2 = new Group();
        Group everyone = new Group();
        everyone.setId(1L);
        when(groupRepository.findByNameIgnoreCase("group1")).thenReturn(group1);
        when(groupRepository.findByNameIgnoreCase("group2")).thenReturn(group2);
        when(groupRepository.findByNameAndOrganisationId(Group.Everyone, orgId)).thenReturn(everyone);

        userService.addToGroups(user, "group1,group2,group1");
        verify(userGroupRepository, times(3)).save(userGroupArgumentCaptor.capture());

        List<UserGroup> allValues = userGroupArgumentCaptor.getAllValues();
        assertEquals(user, allValues.get(0).getUser());
        assertEquals(group1, allValues.get(0).getGroup());
        assertEquals(group2, allValues.get(1).getGroup());
        assertEquals(everyone, allValues.get(2).getGroup());
    }

    @Test
    public void ensureSubjectForUser_shouldSkipWhenSubjectTypeIsVoided() {
        SubjectType voidedSubjectType = new SubjectTypeBuilder()
                .setMandatoryFieldsForNewEntity()
                .setType(Subject.User)
                .build();
        voidedSubjectType.setVoided(true);
        User user = new UserBuilder().organisationId(1L).build();

        userService.ensureSubjectForUser(user, voidedSubjectType);

        verifyNoInteractions(userSubjectRepository);
        verifyNoInteractions(individualRepository);
    }

    @Test
    public void testUserGroupSplitByDelimiter() {
        final String groupsSpecified = "ug1, ug2|ug3 |  ug4-ug5 ,   ug6 ug7";
        String[] userGroups = userService.splitIntoUserGroupNames(groupsSpecified);
        assertArrayEquals(new String[]{"ug1", "ug2", "ug3", "ug4-ug5", "ug6 ug7"}, userGroups);
    }

    @Test
    public void createUser_forSuperAdmin_callsCreateSuperAdminAndSkipsOrgLookup() throws IDPException {
        User user = new UserBuilder().withDefaultValuesForNewEntity().build();
        when(userRepository.save(user)).thenReturn(user);
        IdpService idpService = mock(IdpService.class);
        when(idpServiceFactory.getIdpService()).thenReturn(idpService);

        User result = userService.createUser(user, "S3cure!", Collections.emptyList(), null);

        verify(idpService).createSuperAdmin(user, "S3cure!");
        verifyNoInteractions(organisationRepository);
        assertEquals(user, result);
    }

    @Test(expected = IDPException.class)
    public void createUser_propagatesIDPExceptionFromIdp() throws IDPException {
        long orgId = 1L;
        User user = new UserBuilder().organisationId(orgId).withDefaultValuesForNewEntity().build();
        Organisation organisation = new Organisation();
        OrganisationConfig orgConfig = new OrganisationConfig();
        when(userRepository.save(user)).thenReturn(user);
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(null);
        when(organisationRepository.findOne(orgId)).thenReturn(organisation);
        when(organisationConfigService.getOrganisationConfigByOrgId(orgId)).thenReturn(orgConfig);
        IdpService idpService = mock(IdpService.class);
        when(idpServiceFactory.getIdpService(organisation)).thenReturn(idpService);
        doThrow(new IDPException("password too weak"))
                .when(idpService).createUserWithPassword(user, "weak", orgConfig);

        userService.createUser(user, "weak", Collections.emptyList(), null);
    }

    @Test(expected = AWSCognitoIdentityProviderException.class)
    public void createUser_propagatesAWSCognitoExceptionFromIdp() throws IDPException {
        long orgId = 1L;
        User user = new UserBuilder().organisationId(orgId).withDefaultValuesForNewEntity().build();
        Organisation organisation = new Organisation();
        OrganisationConfig orgConfig = new OrganisationConfig();
        when(userRepository.save(user)).thenReturn(user);
        when(subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User)).thenReturn(null);
        when(organisationRepository.findOne(orgId)).thenReturn(organisation);
        when(organisationConfigService.getOrganisationConfigByOrgId(orgId)).thenReturn(orgConfig);
        IdpService idpService = mock(IdpService.class);
        when(idpServiceFactory.getIdpService(organisation)).thenReturn(idpService);
        doThrow(new AWSCognitoIdentityProviderException("Password did not conform with policy"))
                .when(idpService).createUserWithPassword(user, "weak", orgConfig);

        userService.createUser(user, "weak", Collections.emptyList(), null);
    }

    @Test
    public void createUser_isAnnotatedToRollBackForIDPException() throws NoSuchMethodException {
        Method method = UserService.class.getMethod("createUser", User.class, String.class, List.class, List.class);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull("createUser must be @Transactional for rollback to apply", annotation);
        assertTrue("createUser @Transactional must declare rollbackFor IDPException, otherwise checked-exception failures will commit instead of roll back",
                Arrays.asList(annotation.rollbackFor()).contains(IDPException.class));
    }

    @Test(expected = AWSCognitoIdentityProviderException.class)
    public void updateUser_propagatesAWSCognitoExceptionFromIdp() {
        User user = new UserBuilder().organisationId(1L).withDefaultValuesForNewEntity().build();
        when(accountAdminRepository.findByUser_Id(any())).thenReturn(Collections.emptyList());
        IdpService idpService = mock(IdpService.class);
        when(idpServiceFactory.getIdpService(eq(user), anyBoolean())).thenReturn(idpService);
        doThrow(new AWSCognitoIdentityProviderException("Password did not conform with policy"))
                .when(idpService).updateUser(user);

        userService.updateUser(user, Collections.emptyList(), null);
    }

    @Test
    public void updateUser_isAnnotatedTransactional() throws NoSuchMethodException {
        Method method = UserService.class.getMethod("updateUser", User.class, List.class, List.class);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull("updateUser must be @Transactional for rollback to apply", annotation);
    }
}
