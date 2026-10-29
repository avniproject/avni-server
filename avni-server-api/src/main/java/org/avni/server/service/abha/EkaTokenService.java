package org.avni.server.service.abha;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

// One Eka access token per organisation, held in memory and renewed by a fresh login when under 15 minutes remain.
// The app's ABHA screens are handed the key for a whole registration, so a key for them must have nearly Eka's full 30
// minutes left (getSessionToken); a key that lived only 26 minutes on the phone failed mid-flow on 29 Oct 2026. Most
// session calls therefore log in afresh, which relies on Eka leaving earlier keys valid: seen on 29 Oct, not documented.
// Each cached token carries the settings it was made from, and every call compares them with the stored settings,
// so a saved change takes effect on the next call on every server instance, with nothing to evict.
@Service
public class EkaTokenService {
    private static final Logger logger = LoggerFactory.getLogger(EkaTokenService.class);
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(15);
    private static final Duration SESSION_MIN_REMAINING = Duration.ofMinutes(28);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(10);

    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final EkaCredentialCipher ekaCredentialCipher;
    private final EkaRestClient ekaRestClient;
    private final ConcurrentHashMap<Long, IssuedToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, FailedLogin> failures = new ConcurrentHashMap<>();
    // A lock per organisation, so a slow login for one organisation never holds up another.
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public EkaTokenService(ExternalSystemConfigRepository externalSystemConfigRepository,
                           EkaCredentialCipher ekaCredentialCipher,
                           EkaRestClient ekaRestClient) {
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.ekaCredentialCipher = ekaCredentialCipher;
        this.ekaRestClient = ekaRestClient;
    }

    public EkaToken getAccessToken(Long organisationId) throws EkaNotConfiguredException {
        return getAccessToken(organisationId, RENEW_BEFORE);
    }

    // The key for the app's ABHA screens. Should the renewal fail, a cached key that still clears the bar for server
    // calls is better than refusing the worker.
    public EkaToken getSessionToken(Long organisationId) throws EkaNotConfiguredException {
        try {
            return getAccessToken(organisationId, SESSION_MIN_REMAINING);
        } catch (EkaAuthException e) {
            IssuedToken issued = tokens.get(organisationId);
            if (isUsable(issued, settingsOf(externalSystemConfigRepository.getEkaSystemConfig(organisationId)), RENEW_BEFORE)) {
                return issued.token;
            }
            throw e;
        }
    }

    private EkaToken getAccessToken(Long organisationId, Duration minRemaining) throws EkaNotConfiguredException {
        EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(organisationId);
        if (!StringUtils.hasText(config.getClientId()) || !StringUtils.hasText(config.getEncryptedClientSecret())) {
            throw new EkaNotConfiguredException("Eka client id or secret not set for organisation with id: " + organisationId);
        }
        String settings = settingsOf(config);
        IssuedToken issued = tokens.get(organisationId);
        if (isUsable(issued, settings, minRemaining)) {
            return issued.token;
        }
        synchronized (locks.computeIfAbsent(organisationId, id -> new Object())) {
            issued = tokens.get(organisationId);
            if (isUsable(issued, settings, minRemaining)) {
                return issued.token;
            }
            // Callers queued behind a failed login fail at once, instead of each waiting out a timeout of their own.
            FailedLogin failed = failures.get(organisationId);
            if (failed != null && failed.settings.equals(settings) && failed.at.plus(RETRY_AFTER_FAILURE).isAfter(Instant.now())) {
                throw new EkaAuthException("Eka login failed for organisation " + organisationId
                        + " less than " + RETRY_AFTER_FAILURE.getSeconds() + " s ago; not retrying yet");
            }
            try {
                EkaToken token = ekaRestClient.login(config.getBaseUrl(), config.getClientId(),
                        ekaCredentialCipher.decrypt(config.getEncryptedClientSecret()));
                tokens.put(organisationId, new IssuedToken(settings, token));
                failures.remove(organisationId);
                logger.info("Eka access token issued for organisation {}", organisationId);
                return token;
            } catch (EkaAuthException e) {
                failures.put(organisationId, new FailedLogin(settings, Instant.now()));
                logger.warn("Eka login failed for organisation {}: {}", organisationId, e.getMessage());
                throw e;
            }
        }
    }

    // A request that read the settings before a save can still log in with the old ones; the next call, reading the
    // new settings, sees the mismatch and logs in again.
    private static String settingsOf(EkaSystemConfig config) {
        return config.getEnvironment() + "\n" + config.getClientId() + "\n" + config.getEncryptedClientSecret();
    }

    private static boolean isUsable(IssuedToken issued, String settings, Duration minRemaining) {
        return issued != null
                && issued.settings.equals(settings)
                && issued.token.getExpiresAt().isAfter(Instant.now().plus(minRemaining));
    }

    private static final class IssuedToken {
        private final String settings;
        private final EkaToken token;

        private IssuedToken(String settings, EkaToken token) {
            this.settings = settings;
            this.token = token;
        }
    }

    private static final class FailedLogin {
        private final String settings;
        private final Instant at;

        private FailedLogin(String settings, Instant at) {
            this.settings = settings;
            this.at = at;
        }
    }
}
