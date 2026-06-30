package org.avni.server.service.storage;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Resolves the S3Service for (org, dataClass): a routed named target, else the default backend.
// DEFAULT class always resolves to the default backend (a configured 'default' routing entry is ignored
// but warned about); MODEL misconfiguration fails loud. Built target services are cached, keyed with a
// version component so credential/target edits invalidate the cache.
@Component
public class StorageResolver {
    private static final Logger logger = LoggerFactory.getLogger(StorageResolver.class);

    private final OrganisationConfigService organisationConfigService;
    private final StorageServiceFactory storageServiceFactory;
    private final StorageCredentialProvider credentialProvider;
    private final Map<String, CachedTargetService> targetServiceCache = new ConcurrentHashMap<>();
    private final Set<Long> defaultRoutingChecked = ConcurrentHashMap.newKeySet();

    @Autowired
    public StorageResolver(OrganisationConfigService organisationConfigService,
                           StorageServiceFactory storageServiceFactory,
                           StorageCredentialProvider credentialProvider) {
        this.organisationConfigService = organisationConfigService;
        this.storageServiceFactory = storageServiceFactory;
        this.credentialProvider = credentialProvider;
    }

    public S3Service resolve(Organisation organisation, StorageDataClass dataClass, Supplier<S3Service> defaultProvider) {
        // A null-id organisation (e.g. the eager S3Service singleton built at context startup, where the
        // UserContext holds no real request org) can't be routed or cached - the warn-once set and the cache key
        // both need the id (and a CHM-backed set rejects a null element) - so fall back to default, same as no org.
        if (organisation == null || organisation.getId() == null) {
            return defaultProvider.get();
        }

        if (dataClass == StorageDataClass.DEFAULT) {
            warnOnceIfDefaultRoutingConfigured(organisation);
            return defaultProvider.get();
        }

        OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
        if (organisationConfig == null) {
            return defaultProvider.get();
        }

        String targetName = routedTargetName(organisationConfig, dataClass);
        if (!StringUtils.hasText(targetName)) {
            return defaultProvider.get();
        }

        StorageTarget target = resolveTarget(organisationConfig, targetName);
        String version = versionFor(organisation, target);
        String key = cacheKey(organisation, targetName);

        CachedTargetService cached = targetServiceCache.compute(key, (k, existing) -> {
            if (existing != null && existing.version.equals(version)) {
                return existing;
            }
            // release the superseded client's connection pool
            if (existing != null) {
                existing.shutdown();
            }
            return new CachedTargetService(version, buildTargetService(organisation, target));
        });
        return cached.service;
    }

    // A 'default' storageBackends entry is intentionally not honoured in this release (DEFAULT/media stays on the
    // deploy default to avoid migrating existing media). Surface the ignored, misleading config loudly, but check
    // at most once per org so the DEFAULT/media hot path issues no per-request config read.
    private void warnOnceIfDefaultRoutingConfigured(Organisation organisation) {
        if (!defaultRoutingChecked.add(organisation.getId())) {
            return;
        }
        OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
        if (organisationConfig == null) {
            return;
        }
        String defaultTarget = routedTargetName(organisationConfig, StorageDataClass.DEFAULT);
        if (StringUtils.hasText(defaultTarget)) {
            logger.warn("Organisation '{}' (id {}) configures a '{}' storage routing entry ('{}'), but it is ignored: "
                            + "media stays on the deploy default in this release.",
                    organisation.getName(), organisation.getId(),
                    StorageDataClass.DEFAULT.getConfigName(), defaultTarget);
        }
    }

    @SuppressWarnings("unchecked")
    private String routedTargetName(OrganisationConfig organisationConfig, StorageDataClass dataClass) {
        Object storageBackends = organisationConfig.getConfigValue(OrganisationConfigSettingKey.storageBackends);
        if (storageBackends == null) {
            return null;
        }
        if (!(storageBackends instanceof Map)) {
            throw new StorageConfigurationException(String.format(
                    "'%s' org config must be a map of dataClass -> targetName", OrganisationConfigSettingKey.storageBackends));
        }
        Object targetName = ((Map<String, Object>) storageBackends).get(dataClass.getConfigName());
        return targetName == null ? null : targetName.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private StorageTarget resolveTarget(OrganisationConfig organisationConfig, String targetName) {
        Object targets = organisationConfig.getConfigValue(OrganisationConfigSettingKey.storageTargets);
        if (!(targets instanceof Map)) {
            throw new StorageConfigurationException(String.format(
                    "Org routes a data class to target '%s' but no '%s' map is configured",
                    targetName, OrganisationConfigSettingKey.storageTargets));
        }
        try {
            return StorageTarget.fromConfig(targetName, new JsonObject((Map<String, Object>) targets));
        } catch (RuntimeException e) {
            throw new StorageConfigurationException(String.format(
                    "Failed to resolve storage target '%s': %s", targetName, e.getMessage()), e);
        }
    }

    private S3Service buildTargetService(Organisation organisation, StorageTarget target) {
        try {
            return storageServiceFactory.build(organisation, target);
        } catch (StorageConfigurationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new StorageConfigurationException(String.format(
                    "Failed to resolve storage target '%s' for organisation '%s': %s",
                    target.getName(), organisation.getName(), e.getMessage()), e);
        }
    }

    // Cache-key version: credential last-modified + hash of the target descriptor; either change invalidates the cache.
    private String versionFor(Organisation organisation, StorageTarget target) {
        long credentialVersion = credentialProvider.credentialVersion(organisation, target.getCredentialRef());
        int descriptorHash = Objects.hash(target.getType(), target.getEndpoint(), target.getBucket(), target.getCredentialRef());
        return credentialVersion + ":" + descriptorHash;
    }

    private String cacheKey(Organisation organisation, String targetName) {
        return organisation.getId() + ":" + targetName;
    }

    private static final class CachedTargetService {
        private final String version;
        private final S3Service service;

        private CachedTargetService(String version, S3Service service) {
            this.version = version;
            this.service = service;
        }

        private void shutdown() {
            if (service instanceof TargetStorageService) {
                ((TargetStorageService) service).shutdown();
            }
        }
    }
}
