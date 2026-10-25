package org.avni.server.service.abdm.gateway;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertThrows;

public class AbdmCallbackVerifierTest {
    private static final String CERTS = "/api/hiecm/gateway/v3/certs";
    private WireMockServer gateway;
    private KeyPair keys;

    @Before
    public void setUp() throws Exception {
        gateway = new WireMockServer(options().dynamicPort());
        gateway.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) keys.getPublic();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String n = b64.encodeToString(unsigned(pub.getModulus().toByteArray()));
        String e = b64.encodeToString(unsigned(pub.getPublicExponent().toByteArray()));
        gateway.stubFor(get(urlEqualTo(CERTS)).willReturn(okJson(
                "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}")));
    }

    @After
    public void tearDown() {
        gateway.stop();
    }

    private static byte[] unsigned(byte[] bytes) {
        return bytes.length > 1 && bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private AbdmCallbackVerifier verifier(String mode, String gatewayMode) {
        AbdmConfig cfg = AbdmTestConfig.build(gatewayMode, "http://localhost:" + gateway.port(), "s", mode, gateway.port(), 0);
        return new AbdmCallbackVerifier(cfg);
    }

    private String signed(KeyPair pair) {
        return JWT.create().withKeyId("k1").withIssuer("gateway").withExpiresAt(new Date(System.currentTimeMillis() + 60000))
                .sign(Algorithm.RSA256((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate()));
    }

    private static AbdmCallbackHeaders headers(String authorization) {
        return new AbdmCallbackHeaders("/api/v3/hiu/consent/request/notify", "req-1", null, null, "TANUH-HIU", "sbx", authorization);
    }

    @Test
    public void enforceAcceptsAGatewaySignedToken() {
        verifier("enforce", "sandbox").check(headers("Bearer " + signed(keys)));
    }

    @Test
    public void enforceRefusesATokenSignedByAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String forged = signed(generator.generateKeyPair());

        assertThrows(AbdmCallbackVerifier.CallbackRejected.class, () -> verifier("enforce", "sandbox").check(headers("Bearer " + forged)));
    }

    @Test
    public void enforceRefusesAMissingToken() {
        assertThrows(AbdmCallbackVerifier.CallbackRejected.class, () -> verifier("enforce", "sandbox").check(headers(null)));
    }

    @Test
    public void logModeNeverRefuses() {
        verifier("log", "sandbox").check(headers("Bearer not-a-jwt"));
        verifier("log", "sandbox").check(headers(null));
    }

    @Test
    public void offChecksNothing() {
        verifier("off", "sandbox").check(headers(null));
        gateway.verify(0, getRequestedFor(urlEqualTo(CERTS)));
    }

    @Test
    public void mockModeDoesNotFetchKeys() {
        verifier("log", "mock").check(headers("Bearer mock-gateway"));
        verifier("enforce", "mock").check(headers("Bearer mock-gateway"));
        gateway.verify(0, getRequestedFor(urlEqualTo(CERTS)));
    }
}
