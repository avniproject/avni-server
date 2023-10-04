package org.avni.server.web.controller.oauth;

import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.domain.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
public class JWKSController {
    private final AvniKeycloakConfig avniKeycloakConfig;

    @Autowired
    public JWKSController(AvniKeycloakConfig avniKeycloakConfig) {
        this.avniKeycloakConfig = avniKeycloakConfig;
    }

    // https://www.javadoc.io/doc/com.nimbusds/nimbus-jose-jwt/2.21/com/nimbusds/jose/jwk/RSAKey.html
    @GetMapping("/jwks/publicKey")
    public JsonObject getAvniAsOAuthClientPublicKey() {
        ArrayList<JsonObject> keys = new ArrayList<>();
        JsonObject publicKey = new JsonObject()
                .with("kty", "RSA")
                .with("e", "AQAB")
                .with("use", "enc")
                .with("alg", "RSA-OAEP-256")
                .with("kid", avniKeycloakConfig.getPublicKeyId())
                .with("n", avniKeycloakConfig.getPublicKey());
        keys.add(publicKey);
        return new JsonObject().with("keys", keys);
    }
}
