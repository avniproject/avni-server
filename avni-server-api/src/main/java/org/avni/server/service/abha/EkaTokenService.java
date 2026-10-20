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

// One Eka access token per organisation, held in memory and renewed when under a minute remains.
@Service
public class EkaTokenService {
    private static final Logger logger = LoggerFactory.getLogger(EkaTokenService.class);
    private static final Duration RENEW_BEFORE = Duration.ofSeconds(60);

    private final ExternalSystemConfigRepository externalSystemConfigRepository;
    private final EkaCredentialCipher ekaCredentialCipher;
    private final EkaRestClient ekaRestClient;
    private final ConcurrentHashMap<Long, EkaToken> tokens = new ConcurrentHashMap<>();
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
        EkaToken cached = tokens.get(organisationId);
        if (isFresh(cached)) {
            return cached;
        }
        synchronized (locks.computeIfAbsent(organisationId, id -> new Object())) {
            EkaToken current = tokens.get(organisationId);
            if (isFresh(current)) {
                return current;
            }
            // Read under the lock: evict() after a settings save takes this lock too, so no token made from the
            // old settings outlives the eviction. Only this organisation's callers wait, and they would anyway.
            EkaSystemConfig config = externalSystemConfigRepository.getEkaSystemConfig(organisationId);
            if (!StringUtils.hasText(config.getClientId()) || !StringUtils.hasText(config.getEncryptedClientSecret())) {
                throw new EkaNotConfiguredException("Eka client id or secret not set for organisation with id: " + organisationId);
            }
            String clientSecret = ekaCredentialCipher.decrypt(config.getEncryptedClientSecret());
            EkaToken renewed = renew(organisationId, config, clientSecret, current);
            tokens.put(organisationId, renewed);
            logger.info("Eka access token issued for organisation {}", organisationId);
            return renewed;
        }
    }

    // Waits for a login already under way for this organisation, so the token it brings back is dropped too.
    public void evict(Long organisationId) {
        synchronized (locks.computeIfAbsent(organisationId, id -> new Object())) {
            tokens.remove(organisationId);
        }
    }

    private EkaToken renew(Long organisationId, EkaSystemConfig config, String clientSecret, EkaToken current) {
        String baseUrl = config.getBaseUrl();
        if (canRefresh(current)) {
            try {
                return ekaRestClient.refresh(baseUrl, config.getClientId(), current);
            } catch (EkaAuthException e) {
                logger.warn("Eka token refresh failed for organisation {}, logging in again: {}", organisationId, e.getMessage());
            }
        }
        return ekaRestClient.login(baseUrl, config.getClientId(), clientSecret);
    }

    private static boolean isFresh(EkaToken token) {
        return token != null && token.getExpiresAt().isAfter(Instant.now().plus(RENEW_BEFORE));
    }

    private static boolean canRefresh(EkaToken token) {
        return token != null
                && token.getRefreshToken() != null
                && token.getRefreshExpiresAt() != null
                && token.getRefreshExpiresAt().isAfter(Instant.now().plus(RENEW_BEFORE));
    }
}
