package org.avni.server.service.abha;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EkaRestClientTest {
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";

    private WireMockServer eka;
    private String baseUrl;
    private final EkaRestClient client = new EkaRestClient();

    @Before
    public void setUp() {
        eka = new WireMockServer(options().dynamicPort());
        eka.start();
        baseUrl = "http://localhost:" + eka.port();
    }

    @After
    public void tearDown() {
        eka.stop();
    }

    @Test
    public void loginParsesTokenAndLifetimes() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(okJson(
                "{\"access_token\":\"access-1\",\"expires_in\":1800,\"refresh_token\":\"refresh-1\",\"refresh_expires_in\":3600}")));

        Instant before = Instant.now();
        EkaToken token = client.login(baseUrl, "client-1", "secret-1");

        assertEquals("access-1", token.getAccessToken());
        assertWithinTwoSeconds(before.plusSeconds(1800), token.getExpiresAt());
        eka.verify(postRequestedFor(urlEqualTo(LOGIN_PATH))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("{\"client_id\":\"client-1\",\"client_secret\":\"secret-1\"}")));
    }

    // A gateway that refuses chunked bodies would fail every login for every organisation.
    @Test
    public void loginSendsContentLengthNotChunked() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(okJson("{\"access_token\":\"access-1\",\"expires_in\":1800}")));

        client.login(baseUrl, "client-1", "secret-1");

        eka.verify(postRequestedFor(urlEqualTo(LOGIN_PATH))
                .withHeader("Content-Length", matching("[0-9]+"))
                .withoutHeader("Transfer-Encoding"));
    }

    @Test
    public void loginWithoutTokenThrows() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(okJson("{\"expires_in\":1800}")));
        assertLoginFails();
    }

    @Test
    public void loginWithoutExpiryThrows() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(okJson("{\"access_token\":\"access-1\"}")));
        assertLoginFails();
    }

    @Test
    public void non2xxThrowsEkaAuthException() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid client\"}")));
        assertLoginFails();

        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(aResponse().withStatus(503)));
        assertLoginFails();
    }

    @Test
    public void failsAfterTenSecondsWithoutAnswer() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(okJson(
                "{\"access_token\":\"access-1\",\"expires_in\":1800}").withFixedDelay(11000)));

        // Eka answers at 11 s, so a login that fails proves the client gave up first.
        Instant start = Instant.now();
        assertLoginFails();
        Duration waited = Duration.between(start, Instant.now());

        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(9)) >= 0);
    }

    // Headers arrive at once and the body trickles in over 15 s: the deadline must cover the body too.
    @Test
    public void failsWhenTheBodyStallsPastTenSeconds() {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(okJson(
                "{\"access_token\":\"access-1\",\"expires_in\":1800}").withChunkedDribbleDelay(5, 15000)));

        Instant start = Instant.now();
        assertLoginFails();
        Duration waited = Duration.between(start, Instant.now());

        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(13)) < 0);
    }

    private void assertLoginFails() {
        try {
            client.login(baseUrl, "client-1", "secret-1");
            fail("expected EkaAuthException");
        } catch (EkaAuthException e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("secret-1"));
        }
    }

    private static void assertWithinTwoSeconds(Instant expected, Instant actual) {
        long drift = Math.abs(Duration.between(expected, actual).toMillis());
        assertTrue("expected about " + expected + " but was " + actual, drift <= 2000);
    }
}
