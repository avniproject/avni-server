package org.avni.server.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import jakarta.annotation.PostConstruct;
import org.avni.messaging.domain.EntityType;
import org.avni.server.common.Messageable;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.framework.context.SpringProfiles;
import org.avni.server.util.S;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.avni.server.application.OrganisationConfigSettingKey.donotRequirePasswordChangeOnFirstLogin;

@Service("CognitoIdpService")
@ConditionalOnExpression("'${avni.idp.type}'=='cognito' or '${avni.idp.type}'=='both'")
public class CognitoIdpService extends IdpServiceImpl {
    private static final Logger logger = LoggerFactory.getLogger(CognitoIdpService.class);

    @Value("${aws.accessKeyId}")
    private String accessKeyId;

    @Value("${aws.secretAccessKey}")
    private String secretAccessKey;

    @Value("${cognito.poolid}")
    private String userPoolId;

    @Value("${cognito.region}")
    private String region;

    private AWSCognitoIdentityProvider cognitoClient;

    @Autowired
    public CognitoIdpService(SpringProfiles springProfiles) {
        super(springProfiles);
    }

    @PostConstruct
    public void init() {
        cognitoClient = AWSCognitoIdentityProviderClientBuilder.standard()
                .withCredentials(getCredentialsProvider())
                .withRegion(Regions.fromName(region))
                .build();
        logger.info("Initialized CognitoIDP client");
    }

    private AWSStaticCredentialsProvider getCredentialsProvider() {
        return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretAccessKey));
    }

    private void createUser(User user, String password, boolean changePasswordOnFirstLogin, boolean suppressMessage) {
        logger.info(String.format("Initiating CREATE cognito-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        AdminCreateUserRequest adminCreateUserRequest = new AdminCreateUserRequest()
                .withUserPoolId(userPoolId)
                .withUsername(user.getUsername())
                .withUserAttributes(
                        new AttributeType().withName("email").withValue(user.getEmail()),
                        new AttributeType().withName("phone_number").withValue(user.getPhoneNumber()),
                        new AttributeType().withName("email_verified").withValue("true"),
                        new AttributeType().withName("phone_number_verified").withValue("true"),
                        new AttributeType().withName("custom:userUUID").withValue(user.getUuid())
                )
                .withTemporaryPassword(password);

        if (suppressMessage)
            adminCreateUserRequest = adminCreateUserRequest.withMessageAction(MessageActionType.SUPPRESS);
        AdminCreateUserRequest createUserRequest = adminCreateUserRequest;
        AdminCreateUserResult createUserResult;
        try {
            createUserResult = cognitoClient.adminCreateUser(createUserRequest);
            logger.info(String.format("Created cognito-user | username '%s' | '%s'", user.getUsername(), createUserResult.toString()));
        } catch (UsernameExistsException usernameExistsException) {
            logger.warn("Username: {} exists in Cognito", createUserRequest.getUsername());
        }

        try {
            setPassword(user, password, !changePasswordOnFirstLogin);
        } catch (Exception e) {
            logger.warn(String.format("Username: %s exists in Cognito", createUserRequest.getUsername()), e);
        }
    }

    private void setPassword(User user, String password, boolean permanent) {
        AdminSetUserPasswordRequest updateUserRequest = new AdminSetUserPasswordRequest()
                .withUserPoolId(userPoolId)
                .withUsername(user.getUsername())
                .withPassword(password)
                .withPermanent(permanent);
        cognitoClient.adminSetUserPassword(updateUserRequest);
    }

    @Override
    public void enableUser(User user) {
        logger.info(String.format("Initiating ENABLE cognito-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        AdminEnableUserRequest adminEnableUserRequest = new AdminEnableUserRequest().withUserPoolId(userPoolId).withUsername(user.getUsername());
        cognitoClient.adminEnableUser(adminEnableUserRequest);
        logger.info(String.format("Enabled cognito-user | username '%s'", user.getUsername()));

        this.resendPassword(user);
    }

    @Override
    public void resendPassword(User user) {
        AdminCreateUserRequest adminCreateUserRequest = new AdminCreateUserRequest()
                .withUserPoolId(userPoolId)
                .withUsername(user.getUsername())
                .withUserAttributes(
                        new AttributeType().withName("phone_number").withValue(user.getPhoneNumber())
                )
                .withForceAliasCreation(false)
                .withTemporaryPassword(getDefaultPassword(user))
                .withMessageAction(MessageActionType.RESEND);
        try {
            cognitoClient.adminCreateUser(adminCreateUserRequest);
        } catch (UnsupportedUserStateException e) {
            logger.info(String.format("The user's password couldn't be sent as it was not temporary '%s'", user.getUsername()));
        }
    }

    @Override
    public void disableUser(User user) {
        logger.info(String.format("Initiating DISABLE cognito-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        cognitoClient.adminDisableUser(new AdminDisableUserRequest().withUserPoolId(userPoolId).withUsername(user.getUsername()));
        logger.info(String.format("Disabled cognito-user | username '%s'", user.getUsername()));
    }

    @Override
    public void deleteUser(User user) {
        logger.info(String.format("Initiating DELETE cognito-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        cognitoClient.adminDeleteUser(new AdminDeleteUserRequest().withUserPoolId(userPoolId).withUsername(user.getUsername()));
        logger.info(String.format("Deleted cognito-user | username '%s'", user.getUsername()));
    }

    @Messageable(EntityType.User)
    @Override
    public void createUser(User user, OrganisationConfig organisationConfig) {
        boolean donotRequirePasswordChange = organisationConfig.getBooleanConfigValue(donotRequirePasswordChangeOnFirstLogin);
        createUser(user, getDefaultPassword(user), donotRequirePasswordChange, false);
    }

    @Override
    public void createInActiveUser(User user, OrganisationConfig organisationConfig) throws IDPException {
        createUser(user, getDefaultPassword(user), true, true);
        disableUser(user);
    }

    @Messageable(EntityType.User)
    @Override
    public void createUserWithPassword(User user, String password, OrganisationConfig organisationConfig) {
        boolean donotRequirePasswordChange = organisationConfig.getBooleanConfigValue(donotRequirePasswordChangeOnFirstLogin);
        boolean isTmpPassword = S.isEmpty(password);
        createUser(user, isTmpPassword ? getDefaultPassword(user) : password, !donotRequirePasswordChange, false);
    }

    @Messageable(EntityType.User)
    @Override
    public void createSuperAdmin(User user, String password) {
        boolean isTmpPassword = S.isEmpty(password);
        createUser(user, isTmpPassword ? getDefaultPassword(user) : password, false, false);
    }

    @Override
    public void updateUser(User user) {
        AdminUpdateUserAttributesRequest updateUserRequest = new AdminUpdateUserAttributesRequest()
                .withUserPoolId(userPoolId)
                .withUsername(user.getUsername())
                .withUserAttributes(
                        new AttributeType().withName("email").withValue(user.getEmail()),
                        new AttributeType().withName("phone_number").withValue(user.getPhoneNumber()),
                        new AttributeType().withName("custom:userUUID").withValue(user.getUuid())
                );
        logger.info(String.format("Initiating UPDATE cognito-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        cognitoClient.adminUpdateUserAttributes(updateUserRequest);
        logger.info(String.format("Updated cognito-user | username '%s'", user.getUsername()));
    }

    @Override
    public boolean resetPassword(User user, String password) {
        logger.info(String.format("Initiating reset password cognito-user request | username '%s' | uuid '%s'", user.getUsername(), user.getUuid()));
        setPassword(user, password, true);
        logger.info(String.format("password reset for cognito-user | username '%s'", user.getUsername()));
        return true;
    }

    @Override
    public boolean exists(User user) {
        try {
            cognitoClient.adminGetUser(new AdminGetUserRequest().withUserPoolId(userPoolId).withUsername(user.getUsername()));
            return true;
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    @Override
    public long getLastLoginTime(User user) {
        return -1L;
    }
}
