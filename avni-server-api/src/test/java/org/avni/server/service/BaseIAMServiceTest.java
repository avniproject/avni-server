package org.avni.server.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BaseIAMServiceTest {
    private static final String USERNAME_FIELD = "cognito:username";
    private static final String USER_UUID_FIELD = "custom:userUUID";

    private UserRepository userRepository;
    private BaseIAMService service;

    @Before
    public void setup() {
        userRepository = mock(UserRepository.class);
        service = new BaseIAMService(userRepository) {
            @Override protected String getUserUuidField() { return USER_UUID_FIELD; }
            @Override protected String getUsernameField() { return USERNAME_FIELD; }
            @Override protected void addClaim(Verification verification) { }
            @Override protected String getAudience() { return null; }
            @Override protected String getJwkProviderUrl() { return null; }
            @Override protected String getIssuer() { return null; }
            @Override public void logConfiguration() { }
            @Override public String generateTokenForUser(String username, String password) { return null; }
        };
    }

    private DecodedJWT tokenWith(String username, String userUUID) {
        com.auth0.jwt.JWTCreator.Builder builder = JWT.create();
        if (username != null) builder.withClaim(USERNAME_FIELD, username);
        if (userUUID != null) builder.withClaim(USER_UUID_FIELD, userUUID);
        return JWT.decode(builder.sign(Algorithm.none()));
    }

    private User user(String uuid, String username) {
        User user = new User();
        user.setUuid(uuid);
        user.setUsername(username);
        return user;
    }

    @Test
    public void rejectsTokenWhoseUserUUIDBelongsToAnotherUser() {
        User victim = user("victim-uuid", "victim@org");
        when(userRepository.findByUuid("victim-uuid")).thenReturn(victim);

        DecodedJWT forgedToken = tokenWith("attacker@org", "victim-uuid");

        assertThat(service.getUserFromDecodedToken(forgedToken), is(nullValue()));
    }

    @Test
    public void resolvesUserWhenUsernameMatchesUserUUID() {
        User self = user("self-uuid", "self@org");
        when(userRepository.findByUuid("self-uuid")).thenReturn(self);

        DecodedJWT token = tokenWith("self@org", "self-uuid");

        assertThat(service.getUserFromDecodedToken(token), is(sameInstance(self)));
    }

    @Test
    public void resolvesByUsernameWhenNoUserUUIDClaim() {
        User self = user("self-uuid", "self@org");
        when(userRepository.findByUsername("self@org")).thenReturn(self);

        DecodedJWT token = tokenWith("self@org", null);

        assertThat(service.getUserFromDecodedToken(token), is(sameInstance(self)));
    }
}
