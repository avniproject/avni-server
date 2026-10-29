package org.avni.server.service.abha;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.CryptoService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EkaTokenServiceTest {
    private static final String KEY = "W+6wNuSHHVCnO6tqN2EK5CE0kRa+0mcn/fNmDifSkEU=";
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";
    private static final Long ORG = 1L;
    private static final Long OTHER_ORG = 2L;

    private WireMockServer eka;
    private final ExternalSystemConfigRepository repository = mock(ExternalSystemConfigRepository.class);
    private final EkaCredentialCipher cipher = new EkaCredentialCipher(new CryptoService(), KEY);
    private EkaTokenService service;

    @Before
    public void setUp() throws EkaNotConfiguredException {
        eka = new WireMockServer(options().dynamicPort());
        eka.start();
        service = new EkaTokenService(repository, cipher, new EkaRestClient());
        configure(ORG, "client-1", "secret-1");
        configure(OTHER_ORG, "client-2", "secret-2");
    }

    @After
    public void tearDown() {
        eka.stop();
    }

    @Test
    public void reusesTokenWithinLifetime() throws Exception {
        stubLogin("client-1", "access-1", 1800, 0);

        EkaToken first = service.getAccessToken(ORG);
        EkaToken second = service.getAccessToken(ORG);

        assertEquals("access-1", first.getAccessToken());
        assertSame(first, second);
        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void renewsByLoggingInWhenUnderOneMinuteLeft() throws Exception {
        stubLogin("client-1", "access-1", 30, 0);

        service.getAccessToken(ORG);
        service.getAccessToken(ORG);

        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    // The app is handed this key for a whole ABHA flow (an Aadhaar OTP can take minutes), so a key with ten minutes left
    // is already too short to hand out.
    @Test
    public void renewsWhenUnderFifteenMinutesLeft() throws Exception {
        stubLogin("client-1", "access-1", 600, 0);

        service.getAccessToken(ORG);
        service.getAccessToken(ORG);

        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void reusesTokenWithMoreThanFifteenMinutesLeft() throws Exception {
        stubLogin("client-1", "access-1", 1000, 0);

        service.getAccessToken(ORG);
        service.getAccessToken(ORG);

        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    // The key handed to the app's ABHA screens must outlast a whole registration, so it carries nearly Eka's full
    // 30 minutes: a cached key good enough for a server call (15 minutes) is renewed for a session.
    @Test
    public void aSessionKeyIsRenewedWhenUnderTwentyEightMinutesLeft() throws Exception {
        stubLogin("client-1", "access-1", 1200, 0);

        service.getAccessToken(ORG);
        service.getSessionToken(ORG);

        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void aSessionKeyWithMoreThanTwentyEightMinutesLeftIsReused() throws Exception {
        stubLogin("client-1", "access-1", 1800, 0);

        EkaToken first = service.getSessionToken(ORG);
        EkaToken second = service.getSessionToken(ORG);

        assertSame(first, second);
        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void otherCallersReuseTheKeyASessionRenewed() throws Exception {
        stubLogin("client-1", "access-1", 1200, 0);

        service.getAccessToken(ORG);
        EkaToken renewed = service.getSessionToken(ORG);
        EkaToken reused = service.getAccessToken(ORG);

        assertSame(renewed, reused);
        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    // Should Eka's keys last under 28 minutes, each session logs in afresh and gets that fresh key: no loop, no refusal.
    @Test
    public void whenEkaKeysLastUnderTwentyEightMinutesEachSessionLogsInAndGetsTheFreshKey() throws Exception {
        stubLogin("client-1", "access-1", 1200, 0);

        assertEquals("access-1", service.getSessionToken(ORG).getAccessToken());
        assertEquals("access-1", service.getSessionToken(ORG).getAccessToken());

        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    // A failed renewal for a session does not refuse the worker while the cached key still has 15 minutes.
    @Test
    public void aFailedSessionLoginFallsBackToTheCachedKeyWhileItHasFifteenMinutesLeft() throws Exception {
        stubLogin("client-1", "access-1", 1200, 0);
        EkaToken cached = service.getAccessToken(ORG);
        eka.resetMappings();
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(aResponse().withStatus(401)));

        assertSame(cached, service.getSessionToken(ORG));
        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void failedLoginIsNotRetriedForTenSeconds() throws Exception {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).willReturn(aResponse().withStatus(401)));

        assertLoginFails(ORG);
        assertLoginFails(ORG);

        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void changedSettingsRetryAtOnceAfterAFailure() throws Exception {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH)).withRequestBody(matchingJsonPath("$.client_id", equalTo("client-1")))
                .willReturn(aResponse().withStatus(401)));
        assertLoginFails(ORG);

        configure(ORG, "client-9", "secret-9");
        stubLogin("client-9", "access-9", 1800, 0);

        assertEquals("access-9", service.getAccessToken(ORG).getAccessToken());
    }

    @Test(expected = EkaNotConfiguredException.class)
    public void notConfiguredThrows() throws Exception {
        when(repository.getEkaSystemConfig(3L)).thenThrow(new EkaNotConfiguredException("not configured"));
        service.getAccessToken(3L);
    }

    @Test
    public void missingSecretIsNotConfigured() throws Exception {
        EkaSystemConfig config = mock(EkaSystemConfig.class);
        when(config.getBaseUrl()).thenReturn(baseUrl());
        when(config.getClientId()).thenReturn("client-3");
        when(repository.getEkaSystemConfig(3L)).thenReturn(config);

        try {
            service.getAccessToken(3L);
            fail("expected EkaNotConfiguredException");
        } catch (EkaNotConfiguredException expected) {
        }
        eka.verify(0, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void concurrentCallersLogInOnce() throws Exception {
        stubLogin("client-1", "access-1", 1800, 500);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EkaToken>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.getAccessToken(ORG);
            }));
        }
        Instant begun = Instant.now();
        start.countDown();

        for (Future<EkaToken> result : results) {
            assertEquals("access-1", result.get(30, TimeUnit.SECONDS).getAccessToken());
        }
        Duration waited = Duration.between(begun, Instant.now());
        pool.shutdown();
        assertTrue("a 0.5 s login took " + waited, waited.compareTo(Duration.ofSeconds(5)) < 0);
        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void slowLoginForOneOrganisationDoesNotHoldUpAnother() throws Exception {
        stubLogin("client-1", "access-1", 1800, 3000);
        stubLogin("client-2", "access-2", 1800, 0);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<EkaToken> slow = pool.submit(() -> service.getAccessToken(ORG));
        Thread.sleep(300);

        Instant start = Instant.now();
        assertEquals("access-2", service.getAccessToken(OTHER_ORG).getAccessToken());
        Duration waited = Duration.between(start, Instant.now());

        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(2)) < 0);
        assertEquals("access-1", slow.get(10, TimeUnit.SECONDS).getAccessToken());
        pool.shutdown();
    }

    // Every call compares the stored settings with those the cached token was made from, so a save takes effect on
    // the next call, on every server instance.
    @Test
    public void changedSettingsGetANewTokenOnTheNextCall() throws Exception {
        stubLogin("client-1", "access-1", 1800, 0);
        assertEquals("access-1", service.getAccessToken(ORG).getAccessToken());

        configure(ORG, "client-9", "secret-9");
        stubLogin("client-9", "access-9", 1800, 0);

        assertEquals("access-9", service.getAccessToken(ORG).getAccessToken());
        assertEquals("access-9", service.getAccessToken(ORG).getAccessToken());
        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void loginUnderWayWithOldSettingsIsReplacedOnTheNextCall() throws Exception {
        stubLogin("client-1", "access-1", 1800, 1000);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<EkaToken> inFlight = pool.submit(() -> service.getAccessToken(ORG));
        Thread.sleep(300);

        configure(ORG, "client-9", "secret-9");
        stubLogin("client-9", "access-9", 1800, 0);

        assertEquals("the token from the old settings must not be served", "access-9", service.getAccessToken(ORG).getAccessToken());
        assertEquals("access-1", inFlight.get(10, TimeUnit.SECONDS).getAccessToken());
        pool.shutdown();
    }

    // While Eka is slow, callers queued behind a login share its failure instead of each waiting out their own.
    @Test
    public void concurrentCallersShareOneFailedLogin() throws Exception {
        stubLogin("client-1", "access-1", 1800, 11000);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EkaToken>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.getAccessToken(ORG);
            }));
        }
        Instant begun = Instant.now();
        start.countDown();

        for (Future<EkaToken> result : results) {
            try {
                result.get(30, TimeUnit.SECONDS);
                fail("expected the login to fail");
            } catch (ExecutionException e) {
                assertTrue(e.getCause().toString(), e.getCause() instanceof EkaAuthException);
            }
        }
        Duration waited = Duration.between(begun, Instant.now());
        pool.shutdown();
        assertTrue("waited " + waited, waited.compareTo(Duration.ofSeconds(13)) < 0);
        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    // Runs as staging and live do, with org.avni's additivity off, so the server's own lines stop at org.avni and never
    // reach ROOT. Captures at both, with everything at DEBUG.
    @Test
    public void tokenNeverLogged() throws Exception {
        Logger avni = (Logger) LoggerFactory.getLogger("org.avni");
        boolean avniAdditive = avni.isAdditive();
        List<Logger> loggers = Arrays.asList((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME), avni);
        List<Level> originalLevels = new ArrayList<>();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        for (Logger logger : loggers) {
            originalLevels.add(logger.getLevel());
            logger.addAppender(appender);
            logger.setLevel(Level.DEBUG);
        }
        avni.setAdditive(false);
        try {
            stubLogin("client-1", "access-1", 30, 0);
            service.getAccessToken(ORG);
            service.getAccessToken(ORG);
            configure(ORG, "client-2", "secret-2");
            eka.stubFor(post(urlEqualTo(LOGIN_PATH)).withRequestBody(matchingJsonPath("$.client_id", equalTo("client-2")))
                    .willReturn(aResponse().withStatus(401)));
            assertLoginFails(ORG);
        } finally {
            for (int i = 0; i < loggers.size(); i++) {
                loggers.get(i).detachAppender(appender);
                loggers.get(i).setLevel(originalLevels.get(i));
            }
            avni.setAdditive(avniAdditive);
        }

        assertTrue("the issued-token line must be captured, or the check proves nothing. " + loggingState(),
                appender.list.stream().anyMatch(e -> e.getLoggerName().equals(EkaTokenService.class.getName()) && e.getLevel() == Level.INFO));
        assertTrue("the failed-login line must be captured, or the check proves nothing",
                appender.list.stream().anyMatch(e -> e.getLoggerName().equals(EkaTokenService.class.getName()) && e.getLevel() == Level.WARN));
        for (ILoggingEvent event : appender.list) {
            if (isStubServer(event.getLoggerName())) continue;
            String text = event.getFormattedMessage() + throwableText(event.getThrowableProxy());
            for (String secret : new String[]{"secret-1", "secret-2", "access-1"}) {
                assertFalse(event.getLoggerName() + " logged " + secret + ": " + text, text.contains(secret));
            }
        }
    }

    private static String loggingState() {
        ch.qos.logback.classic.LoggerContext context = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        StringBuilder out = new StringBuilder("turboFilters=" + context.getTurboFilterList() + " started=" + context.isStarted() + " chain=");
        for (String name : new String[]{EkaTokenService.class.getName(), "org.avni.server.service.abha", "org.avni.server.service", "org.avni.server", "org.avni", org.slf4j.Logger.ROOT_LOGGER_NAME}) {
            Logger logger = context.getLogger(name);
            out.append(name).append("[level=").append(logger.getLevel()).append(",effective=").append(logger.getEffectiveLevel())
                    .append(",additive=").append(logger.isAdditive()).append("] ");
        }
        return out.toString();
    }

    // WireMock is the stand-in for Eka; what it logs about the requests it receives is not the server's logging.
    private static boolean isStubServer(String loggerName) {
        return loggerName.startsWith("com.github.tomakehurst") || loggerName.startsWith("wiremock") || loggerName.startsWith("org.eclipse.jetty");
    }

    private static String throwableText(IThrowableProxy proxy) {
        StringBuilder text = new StringBuilder();
        for (IThrowableProxy p = proxy; p != null; p = p.getCause()) {
            text.append(' ').append(p.getMessage());
        }
        return text.toString();
    }

    private void configure(Long organisationId, String clientId, String clientSecret) throws EkaNotConfiguredException {
        EkaSystemConfig config = mock(EkaSystemConfig.class);
        when(config.getBaseUrl()).thenAnswer(invocation -> baseUrl());
        when(config.getClientId()).thenReturn(clientId);
        when(config.getEncryptedClientSecret()).thenReturn(cipher.encrypt(clientSecret));
        when(repository.getEkaSystemConfig(organisationId)).thenReturn(config);
    }

    private String baseUrl() {
        return "http://localhost:" + eka.port();
    }

    private void stubLogin(String clientId, String accessToken, int expiresIn, int delayMillis) {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH))
                .withRequestBody(matchingJsonPath("$.client_id", equalTo(clientId)))
                .willReturn(okJson("{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn + "}").withFixedDelay(delayMillis)));
    }

    private void assertLoginFails(Long organisationId) throws EkaNotConfiguredException {
        try {
            service.getAccessToken(organisationId);
            fail("expected EkaAuthException");
        } catch (EkaAuthException expected) {
        }
    }
}
