package org.avni.server.service.abha;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EkaRestClientTest {
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";
    private static final String CARD_PATH = "/abdm/v1/profile/asset/card";
    // A PNG signature and then text standing in for the picture. Never a real card.
    private static final byte[] CARD = png("stand-in-card-image".getBytes(StandardCharsets.US_ASCII));

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

    @Test
    public void cardRequestCarriesTheEkaIdTokenAndHealthCentre() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "image/png").withBody(CARD)));

        byte[] card = client.getAbhaCard(baseUrl, "access-1", "oid-1", "HIP-1");

        assertArrayEquals(CARD, card);
        eka.verify(1, getRequestedFor(urlPathEqualTo(CARD_PATH))
                .withQueryParam("oid", equalTo("oid-1"))
                .withHeader("Authorization", equalTo("Bearer access-1"))
                .withHeader("X-Pt-Id", equalTo("oid-1"))
                .withHeader("X-Hip-Id", equalTo("HIP-1"))
                .withHeader("Accept", equalTo("image/png")));
    }

    // An Eka ID holding characters that mean something in a URL still names the one patient.
    @Test
    public void ekaIdIsEncodedInTheQuery() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withBody(CARD)));

        client.getAbhaCard(baseUrl, "access-1", "oid 1&oid=2", "HIP-1");

        eka.verify(getRequestedFor(urlPathEqualTo(CARD_PATH)).withQueryParam("oid", equalTo("oid 1&oid=2")));
    }

    // The Eka ID comes from the phone. One that cannot travel in a header fails as Eka failing would, not as a server error.
    @Test
    public void ekaIdThatCannotBeAHeaderThrowsWithoutCallingEka() {
        try {
            client.getAbhaCard(baseUrl, "access-1", "oid-1\r\nX-Injected: 1", "HIP-1");
            fail("expected EkaCardException");
        } catch (EkaCardException e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("access-1") && !e.getMessage().contains("oid-1"));
        }
        eka.verify(0, getRequestedFor(urlPathEqualTo(CARD_PATH)));
    }

    // Eka answers its errors as JSON. None of it is the card, and none of it is read.
    @Test
    public void cardRefusedByEkaThrows() {
        for (int status : new int[]{400, 401, 403, 404, 500, 503}) {
            eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(status)
                    .withHeader("Content-Type", "application/json").withBody("{\"code\":" + status + ",\"error\":\"no\"}")));
            assertCardFails();
        }
    }

    // Eka's own status for a patient whose ABHA login at Eka has lapsed, about 30 minutes after it. The body,
    // {"error":"SessionExpiredTryAgain","code":491}, is not read: the status alone says it.
    @Test
    public void cardForAPatientWhoseLoginAtEkaLapsedThrowsSessionExpired() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(491)
                .withHeader("Content-Type", "application/json").withBody("{\"error\":\"SessionExpiredTryAgain\",\"code\":491}")));
        try {
            client.getAbhaCard(baseUrl, "access-1", "oid-1", "HIP-1");
            fail("expected EkaSessionExpiredException");
        } catch (EkaSessionExpiredException e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("access-1") && !e.getMessage().contains("oid-1"));
        }
    }

    // What Eka's sandbox actually answered on 26 Oct 2026, about 28 minutes after the patient's OTP, while its session
    // check still said the patient was logged in. Only verifying the ABHA again brings the card back.
    @Test
    public void cardRefusedForAnInvalidXTokenThrowsSessionExpired() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json").withBody("{\"error\":\"Invalid X-token\",\"code\":400}")));
        try {
            client.getAbhaCard(baseUrl, "access-1", "oid-1", "HIP-1");
            fail("expected EkaSessionExpiredException");
        } catch (EkaSessionExpiredException e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("access-1") && !e.getMessage().contains("oid-1"));
        }
    }

    // The app is told it gets a PNG. Anything else, such as an error written with 200, is not served as one.
    @Test
    public void answerThatIsNotAPngThrows() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(okJson("{\"error\":\"not a card\"}")));
        assertCardFails();

        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200)));
        assertCardFails();
    }

    @Test
    public void cardUpToTheCapIsAccepted() {
        byte[] largest = png(new byte[EkaRestClient.MAX_CARD_BYTES - 8]);
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withBody(largest)));

        assertEquals(EkaRestClient.MAX_CARD_BYTES, client.getAbhaCard(baseUrl, "access-1", "oid-1", "HIP-1").length);
    }

    // Declared with a Content-Length: refused on the length alone.
    @Test
    public void cardDeclaredLargerThanTheCapThrows() {
        byte[] tooLarge = png(new byte[EkaRestClient.MAX_CARD_BYTES - 7]);
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Length", String.valueOf(tooLarge.length)).withBody(tooLarge)));
        assertCardFails();
    }

    // Sent in chunks with no length declared: the cap stops it as it arrives.
    @Test
    public void chunkedCardLargerThanTheCapThrows() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200)
                .withBody(png(new byte[EkaRestClient.MAX_CARD_BYTES])).withChunkedDribbleDelay(20, 200)));
        assertCardFails();
    }

    @Test
    public void cardFailsAfterTenSecondsWithoutAnswer() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withBody(CARD).withFixedDelay(11000)));

        Instant start = Instant.now();
        assertCardFails();
        Duration waited = Duration.between(start, Instant.now());

        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(9)) >= 0 && waited.compareTo(Duration.ofSeconds(11)) < 0);
    }

    // Headers arrive at once and the picture trickles in over 15 s: the deadline must cover the body too.
    @Test
    public void cardFailsWhenTheBodyStallsPastTenSeconds() {
        eka.stubFor(get(urlPathEqualTo(CARD_PATH)).willReturn(aResponse().withStatus(200).withBody(CARD).withChunkedDribbleDelay(5, 15000)));

        Instant start = Instant.now();
        assertCardFails();
        Duration waited = Duration.between(start, Instant.now());

        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(13)) < 0);
    }

    // The token is a bearer credential and the Eka ID names a patient: neither goes into a message that may be logged.
    private void assertCardFails() {
        try {
            client.getAbhaCard(baseUrl, "access-1", "oid-1", "HIP-1");
            fail("expected EkaCardException");
        } catch (EkaCardException e) {
            assertTrue(e.getMessage(), !e.getMessage().contains("access-1") && !e.getMessage().contains("oid-1"));
            assertFalse("only Eka's 491 says the patient's login lapsed", e instanceof EkaSessionExpiredException);
        }
    }

    private static byte[] png(byte[] content) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] png = Arrays.copyOf(signature, signature.length + content.length);
        System.arraycopy(content, 0, png, signature.length, content.length);
        return png;
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
