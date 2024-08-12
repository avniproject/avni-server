package org.avni.server.service;

import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.exceptions.TokenExpiredException;
import org.avni.server.config.IdpType;
import org.avni.server.dao.AccountAdminRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.AccountAdmin;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.accessControl.AvniNoUserSessionException;
import org.avni.server.framework.security.AuthService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class AuthServiceTest {
    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KeycloakAuthService keycloakAuthService;
    @Mock
    private CognitoAuthServiceImpl cognitoAuthService;
    @Mock
    private AccountAdminRepository accountAdminRepository;
    @Mock
    private UserService userService;
    private User user;
    private AuthService authService;
    private AccountAdmin accountAdmin;

    @Before
    public void setup() {
        initMocks(this);
        authService = new AuthService(userRepository, organisationRepository, accountAdminRepository,
                new IdpServiceFactory(organisationRepository, null, null, cognitoAuthService, keycloakAuthService, IdpType.cognito, null, userService), userService);
        String uuid = "9ecc2805-6528-47ee-8267-9368b266ad39";
        user = new User();
        user.setUuid(uuid);
        user.setOrganisationId(1L);
        accountAdmin = new AccountAdmin();
        accountAdmin.setUser(user);
    }

    @Test (expected = AvniNoUserSessionException.class)
    public void shouldThrowExceptionIfUserNotFound() throws SigningKeyNotFoundException {
        when(cognitoAuthService.getUserFromToken("some token")).thenReturn(null);
        authService.authenticateByToken("some token", null);
    }

    @Test (expected = AvniNoUserSessionException.class)
    public void shouldThrowExceptionIfTokenExpired() throws SigningKeyNotFoundException {
        when(cognitoAuthService.getUserFromToken("some token")).thenThrow(new TokenExpiredException("token expired"));
        authService.authenticateByToken("some token", null);
    }

    @Test
    public void shouldAddOrganisationToContext() throws SigningKeyNotFoundException {
        Organisation organisation = new Organisation();
        when(organisationRepository.findOne(1L)).thenReturn(organisation);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(user);
        when(cognitoAuthService.getUserFromToken("some token")).thenReturn(user);
        UserContext userContext = authService.authenticateByToken("some token", null);
        assertThat(userContext.getOrganisation(), is(equalTo(organisation)));
    }

    @Test
    public void shouldAddRolesToContext() throws SigningKeyNotFoundException {
        Organisation organisation = new Organisation();
        List<AccountAdmin> adminUser = new ArrayList<>();
        adminUser.add(accountAdmin);
        when(organisationRepository.findOne(1L)).thenReturn(organisation);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(user);
        when(cognitoAuthService.getUserFromToken("some token")).thenReturn(user);

        UserContext userContext = authService.authenticateByToken("some token", null);
        assertThat(userContext.getRoles(), contains(User.USER));
        assertThat(userContext.getRoles().size(), is(equalTo(1)));

        user.setAccountAdmin(null);

        userContext = authService.authenticateByToken("some token", null);
        assertThat(userContext.getRoles(), containsInAnyOrder(User.USER));

        user.setAccountAdmin(accountAdmin);
        when(userService.isAdmin(any())).thenReturn(true);
        userContext = authService.authenticateByToken("some token", null);
        assertThat(userContext.getRoles().size(), is(equalTo(0)));

        user.setAccountAdmin(null);
        when(userService.isAdmin(any())).thenReturn(false);
        userContext = authService.authenticateByToken("some token", null);
        assertThat(userContext.getRoles().size(), is(equalTo(1)));
        assertThat(userContext.getRoles(), contains(User.USER));

        user.setAccountAdmin(accountAdmin);
        when(userService.isAdmin(any())).thenReturn(true);
        userContext = authService.authenticateByToken("some token", null);
        assertThat(userContext.getRoles().size(), is(equalTo(0)));
    }

    @Test
    public void shouldSetContextBasedOnUserId() throws SigningKeyNotFoundException {
        Organisation organisation = new Organisation();
        when(organisationRepository.findOne(1L)).thenReturn(organisation);
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(cognitoAuthService.getUserFromToken("some token")).thenReturn(user);
        UserContext userContext = authService.authenticateByUserId(100L, null);
        assertThat(userContext.getUser(), is(equalTo(user)));
        assertThat(userContext.getOrganisation(), is(equalTo(organisation)));
    }
}
