package org.avni.server.service;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.ServerErrorException;
import org.avni.server.domain.User;
import org.joda.time.DateTime;
import org.junit.Test;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeycloakIdpServiceTest {

    public static final String USER_KEYCLOAK_UUID = "user-keycloak-uuid";
    public static final String USERNAME = "user";
    public static final int EXPECTED_NUMBER_OF_EVENTS_TO_FETCH = 5;
    public static final int FIRST_EVENT_INDEX = 0;

    @Test
    public void getLastLoginTimeShouldRetrieveTheLastSession() {
        User user = new User();
        user.setUsername(USERNAME);

        RealmResource realmResource = mock(RealmResource.class);
        UsersResource usersResource = mock(UsersResource.class);

        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId(USER_KEYCLOAK_UUID);
        user.setName(USERNAME);

        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.search(USERNAME, true)).thenReturn(Collections.singletonList(userRepresentation));

        EventRepresentation eventRepresentation1, eventRepresentation2, eventRepresentation3, eventRepresentation4;
        eventRepresentation1 = getLoginEventRepresentation(1);
        eventRepresentation2 = getLoginEventRepresentation(2);
        eventRepresentation3 = getLoginEventRepresentation(3);
        eventRepresentation4 = getLoginEventRepresentation(4);

        KeycloakIdpService keycloakIdpService = new KeycloakIdpService(realmResource, null);

        when(realmResource
                .getEvents(
                        Collections.singletonList(EventType.LOGIN.name()),
                        null, USER_KEYCLOAK_UUID,
                        null,
                        null,
                        null,
                        FIRST_EVENT_INDEX,
                        EXPECTED_NUMBER_OF_EVENTS_TO_FETCH))
                .thenReturn(Arrays.asList(
                                eventRepresentation1,
                                eventRepresentation2,
                                eventRepresentation3,
                                eventRepresentation4));
        assertThat(keycloakIdpService.getLastLoginTime(user))
                .as("When multiple sessions are present, pick the latest minus 1")
                .isEqualTo(eventRepresentation2.getTime());

        when(realmResource
                .getEvents(
                    Collections.singletonList(EventType.LOGIN.name()),
                    null, USER_KEYCLOAK_UUID,
                    null,
                    null,
                    null,
                    FIRST_EVENT_INDEX,
                    EXPECTED_NUMBER_OF_EVENTS_TO_FETCH)).
                thenReturn(Collections.singletonList(eventRepresentation1));
        assertThat(keycloakIdpService.getLastLoginTime(user))
                .as("When only one session is present, there is no previous session, so return -1L")
                .isEqualTo(-1L);



        when(realmResource
                .getEvents(
                    Collections.singletonList(EventType.LOGIN.name()),
                    null, USER_KEYCLOAK_UUID,
                    null,
                    null,
                    null,
                    FIRST_EVENT_INDEX,
                    EXPECTED_NUMBER_OF_EVENTS_TO_FETCH))
                .thenReturn(Collections.emptyList());
        assertThat(keycloakIdpService.getLastLoginTime(user))
                .as("Do not fail if no sessions are returned")
                .isEqualTo(-1L);

        when(realmResource
                .getEvents(
                        Collections.singletonList(EventType.LOGIN.name()),
                        null, USER_KEYCLOAK_UUID,
                        null,
                        null,
                        null,
                        FIRST_EVENT_INDEX,
                        EXPECTED_NUMBER_OF_EVENTS_TO_FETCH)).thenThrow(ForbiddenException.class, ServerErrorException.class);
        assertThat(keycloakIdpService.getLastLoginTime(user))
                .as("Do not fail keycloak events request fails due to client error")
                .isEqualTo(-1L);

        assertThat(keycloakIdpService.getLastLoginTime(user))
                .as("Do not fail keycloak events request fails due to server error")
                .isEqualTo(-1L);
    }

    private static EventRepresentation getLoginEventRepresentation(int hours) {
        EventRepresentation eventRepresentation;
        eventRepresentation = new EventRepresentation();
        eventRepresentation.setUserId(KeycloakIdpServiceTest.USER_KEYCLOAK_UUID);
        eventRepresentation.setType(EventType.LOGIN.name());
        eventRepresentation.setTime(DateTime.now().minusHours(hours).getMillis());
        return eventRepresentation;
    }
}
