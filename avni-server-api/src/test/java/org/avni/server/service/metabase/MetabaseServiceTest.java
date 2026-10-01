package org.avni.server.service.metabase;

import org.avni.server.config.SelfServiceBatchConfig;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.metabase.*;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.domain.metabase.AvniDatabase;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class MetabaseServiceTest {
    private static final String ORG_NAME = "test-org";

    @Mock
    private OrganisationService organisationService;
    @Mock
    private AvniDatabase avniDatabase;
    @Mock
    private MetabaseDatabaseRepository databaseRepository;
    @Mock
    private GroupPermissionsRepository groupPermissionsRepository;
    @Mock
    private CollectionPermissionsRepository collectionPermissionsRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private MetabaseDashboardRepository metabaseDashboardRepository;
    @Mock
    private MetabaseGroupRepository metabaseGroupRepository;
    @Mock
    private MetabaseUserRepository metabaseUserRepository;
    @Mock
    private SelfServiceBatchConfig selfServiceBatchConfig;
    @Mock
    private UserRepository userRepository;

    @Before
    public void setUp() {
        initMocks(this);
        Organisation organisation = mock(Organisation.class);
        when(organisation.getName()).thenReturn(ORG_NAME);
        UserContext userContext = new UserContext();
        userContext.setOrganisation(organisation);
        UserContextHolder.create(userContext);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private MetabaseService metabaseService(boolean selfServiceEnabled) {
        return new MetabaseService(organisationService, avniDatabase, databaseRepository, groupPermissionsRepository,
                collectionPermissionsRepository, collectionRepository, metabaseDashboardRepository,
                metabaseGroupRepository, metabaseUserRepository, databaseRepository, selfServiceBatchConfig,
                userRepository, selfServiceEnabled);
    }

    private UserGroup metabaseUserGroup() {
        User user = new UserBuilder().withDefaultValuesForNewEntity().build();
        user.setEmail("someone@example.com");
        Group group = new Group();
        group.setName(Group.METABASE_USERS);
        return UserGroup.createMembership(user, group);
    }

    @Test
    public void upsertUsersOnMetabase_doesNothingWhenSelfServiceIsDisabled() {
        metabaseService(false).upsertUsersOnMetabase(List.of(metabaseUserGroup()));

        verifyNoInteractions(metabaseGroupRepository);
        verifyNoInteractions(metabaseUserRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    public void upsertUsersOnMetabase_queriesMetabaseWhenSelfServiceIsEnabled() {
        metabaseService(true).upsertUsersOnMetabase(List.of(metabaseUserGroup()));

        verify(metabaseGroupRepository).findGroup(ORG_NAME);
    }

    @Test
    public void upsertUsersOnMetabase_doesNotPropagateWhenMetabaseIsUnreachable() {
        when(metabaseGroupRepository.findGroup(anyString()))
                .thenThrow(new RuntimeException("Connection refused to reporting-green"));

        try {
            metabaseService(true).upsertUsersOnMetabase(List.of(metabaseUserGroup()));
        } catch (RuntimeException e) {
            fail("A Metabase outage must not propagate out of upsertUsersOnMetabase, or it rolls back the caller's transaction. Got: " + e);
        }
    }
}
