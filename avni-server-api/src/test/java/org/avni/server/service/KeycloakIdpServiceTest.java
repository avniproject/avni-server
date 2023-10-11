package org.avni.server.service;

import org.avni.server.domain.User;
import org.joda.time.DateTime;
import org.junit.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeycloakIdpServiceTest {

    @Test
    public void getLastLoginTimeShouldRetrieveTheLastSession() {
        User user = new User();
        user.setId(1L);
        user.setUsername("user");

        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);
        UserResource userResource = mock(UserResource.class);

        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId("1");
        when(usersResource.search("user", true)).thenReturn(Arrays.asList(userRepresentation));

        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("1")).thenReturn(userResource);

        UserSessionRepresentation session1 = new UserSessionRepresentation();
        session1.setStart(DateTime.now().minusHours(1).getMillis());

        UserSessionRepresentation session2 = new UserSessionRepresentation();
        session2.setStart(DateTime.now().minusHours(2).getMillis());

        UserSessionRepresentation session3 = new UserSessionRepresentation();
        session3.setStart(DateTime.now().minusHours(3).getMillis());

        UserSessionRepresentation session4 = new UserSessionRepresentation();
        session4.setStart(DateTime.now().minusHours(4).getMillis());

        KeycloakIdpService keycloakIdpService = new KeycloakIdpService(realmResource, null);

        when(userResource.getUserSessions()).thenReturn(Arrays.asList(session1, session2, session3, session4));
        assertThat(keycloakIdpService.getLastLoginTime(user)).isEqualTo(session2.getStart())
                .as("When multiple sessions are present, pick the latest minus 1");

        when(userResource.getUserSessions()).thenReturn(Collections.singletonList(session1));
        assertThat(keycloakIdpService.getLastLoginTime(user)).isEqualTo(-1L)
                .as("When only one session is present, there is no previous session, so return -1L");


        when(userResource.getUserSessions()).thenReturn(Collections.emptyList());
        assertThat(keycloakIdpService.getLastLoginTime(user)).isEqualTo(-1L)
                .as("Do not fail if no sessions are returned");
    }
}
