package org.avni.server.service;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.core.Response;
import org.avni.messaging.domain.EntityType;
import org.avni.server.common.Messageable;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.framework.context.SpringProfiles;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.events.EventType;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.passay.CharacterData;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.PasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service("KeycloakIdpService")
@ConditionalOnExpression("'${avni.idp.type}'=='keycloak' or '${avni.idp.type}'=='both'")
public class KeycloakIdpService extends IdpServiceImpl {
    public static final String KEYCLOAK_ADMIN_API_CLIENT_ID = "admin-api";
    public static final EventRepresentation DUMMY_LOGIN_EVENT = new EventRepresentation();

    private static final Logger logger = LoggerFactory.getLogger(KeycloakIdpService.class);
    public static final int NUMBER_OF_LOGIN_EVENTS_TO_FETCH = 5;
    public static final int LOGIN_EVENT_OFFSET = 0;
    private final AdapterConfig adapterConfig;

    private RealmResource realmResource;

    @Autowired
    public KeycloakIdpService(SpringProfiles springProfiles, AdapterConfig adapterConfig) {
        super(springProfiles);
        this.adapterConfig = adapterConfig;
        DUMMY_LOGIN_EVENT.setTime(-1L);
    }

    //Testing only
    public KeycloakIdpService(RealmResource realmResource, AdapterConfig adapterConfig) {
        super(null);
        this.realmResource = realmResource;
        this.adapterConfig = adapterConfig;
        DUMMY_LOGIN_EVENT.setTime(-1L);
    }

    @PostConstruct
    public void init() {
        //Is the appending "/auth" required, we cannot set getAuthServerUrl() property with the auth, as its used in KeycloakAuthService without to get certs
        Keycloak keycloak = KeycloakBuilder.builder().serverUrl(adapterConfig.getAuthServerUrl())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS).realm(adapterConfig.getRealm())
                .clientId(KEYCLOAK_ADMIN_API_CLIENT_ID)
                .clientSecret((String) adapterConfig.getCredentials().get("secret"))
                .resteasyClient(new ResteasyClientBuilderImpl().connectionPoolSize(10).build()).build();
        keycloak.tokenManager().getAccessToken();
        realmResource = keycloak.realm(adapterConfig.getRealm());
        Optional<RealmRepresentation> first = keycloak.realms().findAll().stream().findFirst();
        String passwordPolicy = first.get().getPasswordPolicy();
        logger.info("Password policy: " + passwordPolicy);
        logger.info("Initialized keycloak client");
    }

    @Messageable(EntityType.User)
    @Override
    public void createUser(User user, OrganisationConfig organisationConfig) {
        this.createUserWithPassword(user, defaultPassword(user));
    }

    @Override
    public void createInActiveUser(User user, OrganisationConfig organisationConfig) throws IDPException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Messageable(EntityType.User)
    @Override
    public void createUserWithPassword(User user, String password, OrganisationConfig organisationConfig) {
        this.createUserWithPassword(user, password);
    }

    @Messageable(EntityType.User)
    @Override
    public void createSuperAdminWithPassword(User user, String password) {
        this.createUserWithPassword(user, password);
    }

    private UserCreateStatus createUserWithPassword(User user, String password) {
        logger.info(String.format("Initiating create keycloak-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        UserCreateStatus userCreateStatus = new UserCreateStatus(user, UserContextHolder.getUser());
        userCreateStatus.setDefaultPasswordPermanent(true);
        UserRepresentation newUser = getUserRepresentation(user);
        try {
            Response response = realmResource.users().create(newUser);
            logger.info(String.format("created keycloak-user |  Status: %d | Status Info: %s | username '%s' ",
                response.getStatus(), response.getStatusInfo(), user.getUsername()));
            Response.Status.Family family = response.getStatusInfo().getFamily();
            userCreateStatus.setIdpUserCreated(family.equals(Response.Status.Family.INFORMATIONAL)
                    || family.equals(Response.Status.Family.SUCCESSFUL));
        } catch (Exception exception) {
            logger.error(String.format("Failed to create keycloak-user | username '%s' ", user.getUsername()));
            userCreateStatus.setIdpUserCreated(false);
        }
        return userCreateStatus;
    }

    @Override
    public void updateUser(User user) {
        logger.info(String.format("Initiating update keycloak-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        UserRepresentation userRep = getUser(user);
        updateUserRepresentation(user, userRep);
        updateThroughUserRepresentation(userRep);
        logger.info(String.format("updated keycloak-user | username '%s'", user.getUsername()));
    }

    @Override
    public void disableUser(User user) {
        enableOrDisableUser(user, false);
    }

    @Override
    public void deleteUser(User user) {
        logger.info(String.format("Initiating keycloak delete user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        UserRepresentation userRep = getUser(user);
        Response response = realmResource.users().delete(userRep.getId());
        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL ||
                response.getStatusInfo().getFamily() == Response.Status.Family.INFORMATIONAL) {
            logger.info(String.format("delete keycloak-user request | username '%s'", user.getUsername()));
        }
        logger.error(String.format("Failed to delete keycloak-user request | username '%s'", user.getUsername()));
    }

    @Override
    public void enableUser(User user) {
        enableOrDisableUser(user, true);
    }

    @Override
    public boolean resetPassword(User user, String password) throws IDPException {
        logger.info(String.format("Initiating reset password keycloak-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        try {
            realmResource.users().get(getUser(user).getId()).resetPassword(getCredentialRepresentation(password));
            logger.info(String.format("password reset for keycloak-user | username '%s'", user.getUsername()));
            return true;
        } catch (BadRequestException ex) {
            String reason = tryReadResponse(ex);
            logger.error("Error in reset password:" + reason, ex);
            throw new IDPException(reason, ex);
        }
    }

    private static String tryReadResponse(BadRequestException ex) {
        String reason;
        try {
            HashMap<String, Object> result = ObjectMapperSingleton.getObjectMapper().readValue((ByteArrayInputStream) ex.getResponse().getEntity(), HashMap.class);
            reason = (String) result.getOrDefault("error_description", "Key error_description not found in response");
        } catch (IOException e) {
            reason = "Error parsing keycloak response message: " + e.getMessage();
        }
        return reason;
    }

    @Override
    public boolean exists(User user) {
        return !realmResource.users().search(user.getUsername(), true).isEmpty();
    }

    @Override
    public long getLastLoginTime(User user) {
        String userKeycloakUUID;
        try {
            userKeycloakUUID = getUser(user).getId();
            List<EventRepresentation> userLoginEvents = realmResource.getEvents(
                    Collections.singletonList(EventType.LOGIN.name()),
                    null,
                    userKeycloakUUID,
                    null,
                    null,
                    null,
                    LOGIN_EVENT_OFFSET,
                    NUMBER_OF_LOGIN_EVENTS_TO_FETCH);
            EventRepresentation lastLoginEvent = userLoginEvents
                    .stream()
                    .sorted((l, r) -> (int) (r.getTime() - l.getTime())) //sort latest to oldest
                    .skip(1) //exclude the latest(current) login
                    .findFirst()
                    .orElse(DUMMY_LOGIN_EVENT);

            return lastLoginEvent.getTime();
        } catch (ClientErrorException | ServerErrorException ex) {
            logger.error(String.format("Error fetching login events for user %s", user.getUsername()), ex);
        }
        return -1;
    }

    @Override
    public void activateUser(User user) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private CredentialRepresentation getCredentialRepresentation(String password) {
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setType(CredentialRepresentation.PASSWORD);
        cred.setTemporary(false);
        cred.setValue(password);
        return cred;
    }

    private UserRepresentation getUserRepresentation(User user) {
        UserRepresentation newUser = new UserRepresentation();
        updateUserRepresentation(user, newUser);
        newUser.setEmailVerified(true);
        return newUser;
    }

    private void updateUserRepresentation(User user, UserRepresentation userRep) {
        userRep.setUsername(user.getUsername());
        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("phone_number", Collections.singletonList(user.getPhoneNumber()));
        attrs.put("phone_number_verified", Collections.singletonList("true"));
        attrs.put("custom:userUUID", Collections.singletonList(user.getUuid()));
        userRep.setAttributes(attrs);
        userRep.setEmail(user.getEmail());
        userRep.setEnabled(!user.isVoided());
        userRep.setFirstName(user.getName());
    }

    private void enableOrDisableUser(User user, boolean enable) {
        logger.info(String.format("Initiating enable/disable keycloak-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        UserRepresentation userRep = getUser(user);
        userRep.setEnabled(enable);
        updateThroughUserRepresentation(userRep);
        logger.info(String.format("enabled/disabled keycloak-user request | username '%s' | enabled '%s'", user.getUsername(), enable));
    }

    private void updateThroughUserRepresentation(UserRepresentation userRep) {
        realmResource.users().get(userRep.getId()).update(userRep);
    }

    private UserRepresentation getUser(User user) {
        return realmResource.users().search(user.getUsername(), true).stream()
                .findFirst()
                .orElseThrow(EntityNotFoundException::new);
    }

    public String defaultPassword(User user) {
        //Sample policy: length(8) and specialChars(1) and upperCase(1) and lowerCase(1) and digits(1) and notUsername(undefined) and notEmail(undefined)
        CharacterData asciiSpecialCharacters = new CharacterData() {
            @Override
            public String getErrorCode() {
                return "INSUFFICIENT_ASCIISPECIAL";
            }

            @Override
            public String getCharacters() {
                return "~!@#$%^&*()_+{}|:\"<>?,./;'[]-=\\";
            }
        };
        String generatedPassword = new PasswordGenerator().generatePassword(8,
                new CharacterRule(EnglishCharacterData.LowerCase, 1),
                new CharacterRule(EnglishCharacterData.UpperCase, 1),
                new CharacterRule(EnglishCharacterData.Digit, 1),
                new CharacterRule(asciiSpecialCharacters, 1));
        return generatedPassword;
    }
}
