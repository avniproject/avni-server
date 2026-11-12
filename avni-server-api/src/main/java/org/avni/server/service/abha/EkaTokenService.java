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

// Eka access tokens per organisation, one for each of its two Eka clients, held in memory and renewed by a fresh login
// when under 15 minutes remain. The M1 client's token is handed to the app for a whole ABHA flow, so it must outlast one;
// Eka's keys last 30 minutes. The sharing client's token is for record sharing only and never leaves the server.
// Each cached token carries the settings it was made from, and every call compares them with the stored settings,
// so a saved change takes effect on the next call on every server instance, with nothing to evict.
@Service
public class EkaTokenService {
    private static final Logger logger = LoggerFactory.getLogger(EkaTokenService.class);
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(15);
    private static final Duration SESSION_MIN_REMAINING = Duration.ofMinutes(28);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(10);

    private enum Client {M1, SHARING}

    private record Key(Long organisationId, Client client) {
    }

    private record Credentials(String clientId, String encryptedSecret) {
    }

    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final EkaCredentialCipher ekaCredentialCipher;
    private final EkaRestClient ekaRestClient;
    private final ConcurrentHashMap<Key, IssuedToken> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, FailedLogin> failures = new ConcurrentHashMap<>();
    // A lock per organisation and client, so a slow login never holds up another organisation or the other client.
    private final ConcurrentHashMap<Key, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public EkaTokenService(ExternalSystemConfigRepository externalSystemConfigRepository,
                           EkaCredentialCipher ekaCredentialCipher,
                           EkaRestClient ekaRestClient) {
        this.externalSystemConfigRepository = externalSystemConfigRepository;
        this.ekaCredentialCipher = ekaCredentialCipher;
        this.ekaRestClient = ekaRestClient;
    }

    // The M1 client's token: ABHA sessions on the phone and the ABHA card.
    public EkaToken getAccessToken(Long organisationId) throws EkaNotConfiguredException {
        return getToken(new Key(organisationId, Client.M1), RENEW_BEFORE);
    }

    public EkaToken getSessionToken(Long organisationId) throws EkaNotConfiguredException {
        Key key = new Key(organisationId, Client.M1);
        try {
            return getToken(key, SESSION_MIN_REMAINING);
        } catch (EkaAuthException e) {
            IssuedToken issued = tokens.get(key);
            EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(organisationId);
            if (isUsable(issued, settingsOf(config, credentialsOf(config, Client.M1)), RENEW_BEFORE)) {
                return issued.token;
            }
            throw e;
        }
    }

    // The sharing client's token: record sharing (EkaAbdmClient). Never handed to the app.
    public EkaToken getSharingToken(Long organisationId) throws EkaNotConfiguredException {
        return getToken(new Key(organisationId, Client.SHARING), RENEW_BEFORE);
    }

    private EkaToken getToken(Key key, Duration minRemaining) throws EkaNotConfiguredException {
        Long organisationId = key.organisationId();
        EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(organisationId);
        Credentials credentials = credentialsOf(config, key.client());
        if (!StringUtils.hasText(credentials.clientId()) || !StringUtils.hasText(credentials.encryptedSecret())) {
            throw new EkaNotConfiguredException("Eka " + describe(key.client()) + " id or secret not set for organisation with id: " + organisationId);
        }
        // The phone holds the M1 client's token: record sharing never logs in as that client.
        if (key.client() == Client.SHARING && credentials.clientId().trim().equals(config.getClientId() == null ? null : config.getClientId().trim())) {
            throw new EkaNotConfiguredException("Eka record-sharing client is the M1 client for organisation with id: " + organisationId);
        }
        String settings = settingsOf(config, credentials);
        IssuedToken issued = tokens.get(key);
        if (isUsable(issued, settings, minRemaining)) {
            return issued.token;
        }
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            issued = tokens.get(key);
            if (isUsable(issued, settings, minRemaining)) {
                return issued.token;
            }
            // Callers queued behind a failed login fail at once, instead of each waiting out a timeout of their own.
            FailedLogin failed = failures.get(key);
            if (failed != null && failed.settings.equals(settings) && failed.at.plus(RETRY_AFTER_FAILURE).isAfter(Instant.now())) {
                throw new EkaAuthException("Eka " + describe(key.client()) + " login failed for organisation " + organisationId
                        + " less than " + RETRY_AFTER_FAILURE.getSeconds() + " s ago; not retrying yet");
            }
            try {
                EkaToken token = ekaRestClient.login(config.getBaseUrl(), credentials.clientId(),
                        ekaCredentialCipher.decrypt(credentials.encryptedSecret()));
                tokens.put(key, new IssuedToken(settings, token));
                failures.remove(key);
                logger.info("Eka access token issued for the {} of organisation {}", describe(key.client()), organisationId);
                return token;
            } catch (EkaAuthException e) {
                failures.put(key, new FailedLogin(settings, Instant.now()));
                logger.warn("Eka login failed for the {} of organisation {}: {}", describe(key.client()), organisationId, e.getMessage());
                throw e;
            }
        }
    }

    private static Credentials credentialsOf(EkaSystemConfig config, Client client) {
        return client == Client.SHARING
                ? new Credentials(config.getSharingClientId(), config.getEncryptedSharingClientSecret())
                : new Credentials(config.getClientId(), config.getEncryptedClientSecret());
    }

    private static String describe(Client client) {
        return client == Client.SHARING ? "record-sharing client" : "client";
    }

    // A request that read the settings before a save can still log in with the old ones; the next call, reading the
    // new settings, sees the mismatch and logs in again.
    private static String settingsOf(EkaSystemConfig config, Credentials credentials) {
        return config.getEnvironment() + "\n" + credentials.clientId() + "\n" + credentials.encryptedSecret();
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
