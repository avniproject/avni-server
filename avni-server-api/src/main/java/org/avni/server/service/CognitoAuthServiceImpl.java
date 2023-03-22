package org.avni.server.service;

import com.auth0.jwk.*;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import org.avni.server.config.CognitoConfig;
import org.avni.server.dao.UserRepository;
import org.avni.server.framework.context.SpringProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;

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
