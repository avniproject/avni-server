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
    private static final String REFRESH_PATH = "/connect-auth/v1/account/refresh-token";
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
        stubLogin("client-1", "access-1", 1800, 3600, 0);

        EkaToken first = service.getAccessToken(ORG);
        EkaToken second = service.getAccessToken(ORG);

        assertEquals("access-1", first.getAccessToken());
        assertSame(first, second);
        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void refreshesWhenUnderOneMinuteLeft() throws Exception {
        stubLogin("client-1", "access-1", 30, 3600, 0);
        eka.stubFor(post(urlEqualTo(REFRESH_PATH)).willReturn(okJson(tokenJson("access-2", 1800, 3600))));

        assertEquals("access-1", service.getAccessToken(ORG).getAccessToken());
        assertEquals("access-2", service.getAccessToken(ORG).getAccessToken());

        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
        eka.verify(1, postRequestedFor(urlEqualTo(REFRESH_PATH)).withHeader("Authorization", equalTo("Bearer access-1")));
    }

    @Test
    public void logsInWhenRefreshFails() throws Exception {
        stubLogin("client-1", "access-1", 30, 3600, 0);
        eka.stubFor(post(urlEqualTo(REFRESH_PATH)).willReturn(aResponse().withStatus(401)));

        service.getAccessToken(ORG);
        service.getAccessToken(ORG);

        eka.verify(1, postRequestedFor(urlEqualTo(REFRESH_PATH)));
        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void logsInWhenRefreshTokenExpired() throws Exception {
        stubLogin("client-1", "access-1", 30, 30, 0);

        service.getAccessToken(ORG);
        service.getAccessToken(ORG);

        eka.verify(0, postRequestedFor(urlEqualTo(REFRESH_PATH)));
        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
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
        stubLogin("client-1", "access-1", 1800, 3600, 500);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EkaToken>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.getAccessToken(ORG);
            }));
        }
        start.countDown();

        for (Future<EkaToken> result : results) {
            assertEquals("access-1", result.get(10, TimeUnit.SECONDS).getAccessToken());
        }
        pool.shutdown();
        eka.verify(1, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void slowLoginForOneOrganisationDoesNotHoldUpAnother() throws Exception {
        stubLogin("client-1", "access-1", 1800, 3600, 3000);
        stubLogin("client-2", "access-2", 1800, 3600, 0);
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

    @Test
    public void evictForcesFreshLogin() throws Exception {
        stubLogin("client-1", "access-1", 1800, 3600, 0);

        service.getAccessToken(ORG);
        service.evict(ORG);
        service.getAccessToken(ORG);

        eka.verify(2, postRequestedFor(urlEqualTo(LOGIN_PATH)));
    }

    @Test
    public void evictDuringLoginDropsTheTokenThatLoginBringsBack() throws Exception {
        stubLogin("client-1", "access-1", 1800, 3600, 1000);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<EkaToken> inFlight = pool.submit(() -> service.getAccessToken(ORG));
        Thread.sleep(300);

        configure(ORG, "client-9", "secret-9");
        stubLogin("client-9", "access-9", 1800, 3600, 0);
        service.evict(ORG);

        assertEquals("access-1", inFlight.get(10, TimeUnit.SECONDS).getAccessToken());
        assertEquals("the token from the old settings must not survive the eviction", "access-9", service.getAccessToken(ORG).getAccessToken());
        pool.shutdown();
    }

    @Test
    public void tokenNeverLogged() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level originalLevel = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        try {
            stubLogin("client-1", "access-1", 30, 3600, 0);
            eka.stubFor(post(urlEqualTo(REFRESH_PATH)).willReturn(okJson(tokenJson("access-2", 1800, 3600))));
            service.getAccessToken(ORG);
            service.getAccessToken(ORG);
            eka.stubFor(post(urlEqualTo(REFRESH_PATH)).willReturn(aResponse().withStatus(401)));
            service.evict(ORG);
            stubLogin("client-1", "access-3", 30, 3600, 0);
            service.getAccessToken(ORG);
            service.getAccessToken(ORG);
        } finally {
            root.detachAppender(appender);
            root.setLevel(originalLevel);
        }

        assertFalse("nothing was captured, so the check proves nothing", appender.list.isEmpty());
        for (ILoggingEvent event : appender.list) {
            if (isStubServer(event.getLoggerName())) continue;
            String text = event.getFormattedMessage() + throwableText(event.getThrowableProxy());
            for (String secret : new String[]{"secret-1", "access-1", "access-2", "access-3", "refresh-1", "refresh-access-1"}) {
                assertFalse(event.getLoggerName() + " logged " + secret + ": " + text, text.contains(secret));
            }
        }
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

    private void stubLogin(String clientId, String accessToken, int expiresIn, int refreshExpiresIn, int delayMillis) {
        eka.stubFor(post(urlEqualTo(LOGIN_PATH))
                .withRequestBody(matchingJsonPath("$.client_id", equalTo(clientId)))
                .willReturn(okJson(tokenJson(accessToken, expiresIn, refreshExpiresIn)).withFixedDelay(delayMillis)));
    }

    private static String tokenJson(String accessToken, int expiresIn, int refreshExpiresIn) {
        return "{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn
                + ",\"refresh_token\":\"refresh-" + accessToken + "\",\"refresh_expires_in\":" + refreshExpiresIn + "}";
    }
}
