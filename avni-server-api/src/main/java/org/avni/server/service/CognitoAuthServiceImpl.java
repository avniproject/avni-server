package org.avni.server.service;

import com.auth0.jwt.interfaces.Verification;
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
    private static final String COGNITO_URL = "https://cognito-idp.ap-south-1.amazonaws.com/";
    private final Logger logger = LoggerFactory.getLogger(CognitoAuthServiceImpl.class);

    private final CognitoConfig cognitoConfig;

    @Autowired
    public CognitoAuthServiceImpl(UserRepository userRepository, CognitoConfig cognitoConfig) {
        super(userRepository);
        this.cognitoConfig = cognitoConfig;
    }

    public void logConfiguration() {
        logger.debug("Cognito configuration");
        logger.debug(String.format("Pool Id: %s", cognitoConfig.getPoolId()));
        logger.debug(String.format("Client Id: %s", cognitoConfig.getClientId()));
    }

    protected String getJwkProviderUrl() {
        return this.getIssuer() + "/.well-known/jwks.json";
    }

    protected String getIssuer() {
        return COGNITO_URL + cognitoConfig.getPoolId();
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
}
