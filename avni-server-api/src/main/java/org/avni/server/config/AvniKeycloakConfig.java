package org.avni.server.config;

import org.avni.server.util.FileUtil;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

@Component
public class AvniKeycloakConfig {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AvniKeycloakConfig.class);

    @Value("${avni.keycloak.verify.token.audience}")
    private String verifyTokenAudience;

    @Value("${avni.keycloak.client}")
    private String client;

    @Value("${avni.keycloak.user.email.verified}")
    private String userEmailVerified;

    @Value("${avni.keycloak.user.preferred.username}")
    private String preferredUserName;

    @Value("${avni.keycloak.user.uuid}")
    private String customUserUUID;

    @Value("${avni.keycloak.openid.connect.certs}")
    private String openidConnectCertsUrlFormat;

    @Value("${avni.keycloak.realms}")
    private String realmsUrlFormat;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${avni.keycloak.avni.publicKeyId}")
    private String publicKeyId;

    private String publicKey;
    private PrivateKey privateKey;

    @PostConstruct
    public void postInit() {
        try {
            String avniJWKSPublicKeyFile = "avniJWKSPublicKey";
            if (new File(avniJWKSPublicKeyFile).exists()) {
                publicKey = FileUtil.readStringOfFileOnFileSystem(avniJWKSPublicKeyFile);
            }
        } catch (IOException e) {
            logger.error("Couldn't read public key provided", e);
        }
        try {
            String avniJWKSPrivateKeyFile = "avniJWKSPrivateKey";
            File privKeyFile = new File("avniJWKSPrivateKey");
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(privKeyFile));
            byte[] privKeyBytes = new byte[(int) privKeyFile.length()];
            bis.read(privKeyBytes);
            bis.close();
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            KeySpec ks = new PKCS8EncodedKeySpec(privKeyBytes);
            privateKey = keyFactory.generatePrivate(ks);
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            logger.error("Couldn't read private key provided", e);
        }
    }

    public String getVerifyTokenAudience() {
        return verifyTokenAudience;
    }

    public String getUserEmailVerified() {
        return userEmailVerified;
    }

    public String getPreferredUserName() {
        return preferredUserName;
    }

    public String getCustomUserUUID() {
        return customUserUUID;
    }

    public String getOpenidConnectCertsUrlFormat() {
        return openidConnectCertsUrlFormat;
    }

    public String getRealmsUrlFormat() {
        return realmsUrlFormat;
    }

    public String getRealm() {
        return realm;
    }

    public String getClient() {
        return client;
    }

    public String getPublicKeyId() {
        return publicKeyId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
