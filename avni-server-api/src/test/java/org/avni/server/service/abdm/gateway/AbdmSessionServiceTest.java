package org.avni.server.service.abdm.gateway;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmTestConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;

public class AbdmSessionServiceTest {
    private static final String SESSION_PATH = "/api/hiecm/gateway/v3/sessions";
    private static final String TIMESTAMP = "\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z";

    private WireMockServer gateway;
    private MutableClock clock;
    private AbdmSessionService service;

    @Before
    public void setUp() {
        gateway = new WireMockServer(options().dynamicPort());
        gateway.start();
        clock = new MutableClock(Instant.parse("2026-10-26T04:00:00Z"));
        service = serviceWithSecret(AbdmTestConfig.CLIENT_SECRET);
    }

    @After
    public void tearDown() {
        gateway.stop();
    }

    private AbdmSessionService serviceWithSecret(String secret) {
        AbdmConfig cfg = AbdmTestConfig.sandbox("http://localhost:" + gateway.port(), secret);
        return new AbdmSessionService(cfg, Duration.ofSeconds(2), Duration.ofSeconds(2), clock);
    }

    private void tokenIs(String token, int expiresIn) {
        gateway.stubFor(post(urlEqualTo(SESSION_PATH)).willReturn(okJson(
                "{\"accessToken\":\"" + token + "\",\"expiresIn\":" + expiresIn + ",\"tokenType\":\"bearer\"}")));
    }

    @Test
    public void asksForASessionWithClientCredentialsAndStandardHeaders() {
        tokenIs("token-1", 1200);

        assertEquals("token-1", service.accessToken());

        gateway.verify(postRequestedFor(urlEqualTo(SESSION_PATH))
                .withHeader("REQUEST-ID", matching("[0-9a-f-]{36}"))
                .withHeader("TIMESTAMP", matching(TIMESTAMP))
                .withHeader("X-CM-ID", equalTo("sbx"))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("Content-Length", matching("[0-9]+"))
                .withoutHeader("Transfer-Encoding")
                .withRequestBody(equalToJson("{\"clientId\":\"" + AbdmTestConfig.CLIENT_ID + "\",\"clientSecret\":\""
                        + AbdmTestConfig.CLIENT_SECRET + "\",\"grantType\":\"client_credentials\"}")));
    }

    @Test
    public void cachesTheToken() {
        tokenIs("token-1", 1200);

        assertEquals("token-1", service.accessToken());
        clock.advance(Duration.ofMinutes(10));
        assertEquals("token-1", service.accessToken());

        gateway.verify(1, postRequestedFor(urlEqualTo(SESSION_PATH)));
    }

    @Test
    public void renewsWhenLessThanTwoMinutesRemain() {
        tokenIs("token-1", 300);
        assertEquals("token-1", service.accessToken());

        clock.advance(Duration.ofSeconds(179));
        assertEquals("token-1", service.accessToken());

        tokenIs("token-2", 300);
        clock.advance(Duration.ofSeconds(2));
        assertEquals("token-2", service.accessToken());
        gateway.verify(2, postRequestedFor(urlEqualTo(SESSION_PATH)));
    }

    @Test
    public void expiryDefaultsTo1200SecondsWhenNotGiven() {
        gateway.stubFor(post(urlEqualTo(SESSION_PATH)).willReturn(okJson("{\"accessToken\":\"token-1\"}")));
        assertEquals("token-1", service.accessToken());

        clock.advance(Duration.ofSeconds(1079));
        service.accessToken();
        gateway.verify(1, postRequestedFor(urlEqualTo(SESSION_PATH)));

        clock.advance(Duration.ofSeconds(2));
        service.accessToken();
        gateway.verify(2, postRequestedFor(urlEqualTo(SESSION_PATH)));
    }

    @Test
    public void invalidateForcesANewSession() {
        tokenIs("token-1", 1200);
        service.accessToken();
        tokenIs("token-2", 1200);

        service.invalidate();

        assertEquals("token-2", service.accessToken());
    }

    @Test
    public void backsOffForTenSecondsAfterAFailure() {
        gateway.stubFor(post(urlEqualTo(SESSION_PATH)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"ABDM-1006\",\"message\":\"Invalid client\"}}")));

        AbdmGatewayException first = assertFails();
        assertEquals(400, first.getStatus());
        assertEquals("ABDM-1006", first.getAbdmCode());

        clock.advance(Duration.ofSeconds(5));
        assertFails();
        gateway.verify(1, postRequestedFor(urlEqualTo(SESSION_PATH)));

        tokenIs("token-1", 1200);
        clock.advance(Duration.ofSeconds(6));
        assertEquals("token-1", service.accessToken());
        gateway.verify(2, postRequestedFor(urlEqualTo(SESSION_PATH)));
    }

    @Test
    public void aSessionAnswerWithoutATokenFails() {
        gateway.stubFor(post(urlEqualTo(SESSION_PATH)).willReturn(okJson("{\"expiresIn\":1200}")));
        assertFails();
    }

    @Test
    public void blankSecretInSandboxModeFailsWithoutCallingTheGateway() {
        AbdmSessionService noSecret = serviceWithSecret(" ");
        try {
            noSecret.accessToken();
            fail("expected a failure");
        } catch (AbdmGatewayException e) {
            assertEquals("ABDM_CLIENT_SECRET not set", e.getMessage());
            assertEquals(0, e.getStatus());
        }
        gateway.verify(0, postRequestedFor(urlEqualTo(SESSION_PATH)));
    }

    @Test
    public void neverLogsTheSecretOrTheToken() {
        Logger abdm = (Logger) LoggerFactory.getLogger("org.avni.server.service.abdm");
        Level original = abdm.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        abdm.addAppender(appender);
        abdm.setLevel(Level.TRACE);
        try {
            tokenIs("token-secret-xyz", 1200);
            service.accessToken();
            service.invalidate();
            gateway.stubFor(post(urlEqualTo(SESSION_PATH)).willReturn(aResponse().withStatus(500)
                    .withBody("{\"message\":\"echo " + AbdmTestConfig.CLIENT_SECRET + "\"}")));
            AbdmGatewayException failure = assertFails();
            assertFalse(failure.getMessage().contains(AbdmTestConfig.CLIENT_SECRET));
        } finally {
            abdm.detachAppender(appender);
            abdm.setLevel(original);
        }
        assertFalse("expected the session calls to be logged", appender.list.isEmpty());
        for (ILoggingEvent event : appender.list) {
            String logged = event.getFormattedMessage();
            assertFalse(logged, logged.contains("token-secret-xyz"));
            assertFalse(logged, logged.contains(AbdmTestConfig.CLIENT_SECRET));
        }
    }

    private AbdmGatewayException assertFails() {
        try {
            service.accessToken();
        } catch (AbdmGatewayException e) {
            return e;
        }
        fail("expected a failure");
        return null;
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
