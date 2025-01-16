package org.avni.server.service;

import com.amazonaws.services.cognitoidp.model.AuthenticationResultType;
import com.auth0.jwt.interfaces.Verification;
import org.avni.server.auth.AuthenticationHelper;
import org.avni.server.config.CognitoConfig;
import org.avni.server.dao.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnExpression("'${avni.idp.type}'=='cognito' or '${avni.idp.type}'=='both'")
public class CognitoAuthServiceImpl extends BaseIAMService {

    private final Logger logger = LoggerFactory.getLogger(CognitoAuthServiceImpl.class);

    private final CognitoConfig cognitoConfig;

    @Autowired
    public CognitoAuthServiceImpl(UserRepository userRepository, CognitoConfig cognitoConfig) {
        super(userRepository);
        this.cognitoConfig = cognitoConfig;
    }

    public void logConfiguration() {
        logger.debug("Cognito configuration");
        logger.debug(String.format("Region: %s", cognitoConfig.getRegion()));
        logger.debug(String.format("Pool Id: %s", cognitoConfig.getPoolId()));
        logger.debug(String.format("Client Id: %s", cognitoConfig.getClientId()));
    }

    @Override
    public String generateTokenForUser(String username, String password) {
        AuthenticationHelper helper = new AuthenticationHelper(cognitoConfig.getPoolId(), cognitoConfig.getClientId());
        AuthenticationResultType authenticationResultType = helper.performSRPAuthentication(username, password);
        return authenticationResultType.getIdToken();
    }

    protected String getJwkProviderUrl() {
        return this.getIssuer() + "/.well-known/jwks.json";
    }

    protected String getIssuer() {
        return getCognitoUrl() + cognitoConfig.getPoolId();
    }

    @Override
    protected String getUserUuidField() {
        return "custom:userUUID";
    }

    @Override
    protected String getUsernameField() {
        return "cognito:username";
    }

    @Override
    protected void addClaim(Verification verification) {
        verification.withClaim("token_use", "id");
    }

    @Override
    protected String getAudience() {
        return cognitoConfig.getClientId();
    }

    private String getCognitoUrl() {
        return String.format("https://cognito-idp.%s.amazonaws.com/", cognitoConfig.getRegion());
    }
}
