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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Per-org, per-data-class storage resolver (avniproject/avni-server#1012, D12/D16).
 * <p>
 * Returns the {@link S3Service} for {@code (org, dataClass)}:
 * <ul>
 *     <li>An org with a {@code storageBackends} routing entry for the data class resolves to that
 *     named target (built from {@code storageTargets} + the encrypted-per-org credential store).</li>
 *     <li>Otherwise it resolves to <b>today's default backend</b> (the supplied default provider) -
 *     byte-for-byte unchanged for unconfigured orgs (D16). {@link StorageDataClass#DEFAULT} ALWAYS
 *     resolves to the default provider in P0 (only {@code MODEL} is routable), guaranteeing the whole
 *     existing media/extension/export/bulk-upload surface is unaffected.</li>
 * </ul>
 * Misconfiguration (unknown target name, malformed descriptor, missing/undecryptable credential)
 * <b>fails safe and loud</b> via {@link StorageConfigurationException} - never a silent fallback.
 * <p>
 * Built per-org-target {@link S3Service}s are cached (each wraps a live S3 client). The cache key
 * includes a <b>version component</b> - the routed credential's last-modified stamp + a hash of the
 * resolved target descriptor - so rotating a credential or editing a target invalidates the cached
 * client without a JVM restart (avniproject/avni-server#1012). When a stale entry is superseded the
 * previous {@link TargetStorageService}'s S3 client is shut down to release its connection pool.
 */
@Component
public class StorageResolver {
    private static final Logger logger = LoggerFactory.getLogger(StorageResolver.class);

    private final OrganisationConfigService organisationConfigService;
    private final StorageServiceFactory storageServiceFactory;
    private final StorageCredentialProvider credentialProvider;
    // org:target -> cached entry (versioned). A change in version supersedes the entry.
    private final Map<String, CachedTargetService> targetServiceCache = new ConcurrentHashMap<>();

    @Autowired
    public StorageResolver(OrganisationConfigService organisationConfigService,
                           StorageServiceFactory storageServiceFactory,
                           StorageCredentialProvider credentialProvider) {
        this.organisationConfigService = organisationConfigService;
        this.storageServiceFactory = storageServiceFactory;
        this.credentialProvider = credentialProvider;
    }

    /**
     * @param organisation    current org (may be null for batch/non-request contexts).
     * @param dataClass       the data class being routed.
     * @param defaultProvider supplies today's default backend (e.g. the batch/minio-aware S3 service).
     *                        Used for {@link StorageDataClass#DEFAULT} and for any unconfigured class.
     */
    public S3Service resolve(Organisation organisation, StorageDataClass dataClass, Supplier<S3Service> defaultProvider) {
        // DEFAULT data, no org context, or an org without routing config => today's backend, unchanged.
        if (dataClass == StorageDataClass.DEFAULT || organisation == null) {
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
            // Supersede a stale entry: release the previous client's connection pool first.
            if (existing != null) {
                existing.shutdown();
            }
            return new CachedTargetService(version, buildTargetService(organisation, target));
        });
        return cached.service;
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

    /**
     * Version stamp for the cache key: the routed credential's last-modified (so credential rotation
     * invalidates) plus a hash of the resolved target descriptor (so endpoint/bucket/type/credentialRef
     * edits invalidate). Either changing supersedes the cached client.
     */
    private String versionFor(Organisation organisation, StorageTarget target) {
        long credentialVersion = credentialProvider.credentialVersion(organisation, target.getCredentialRef());
        int descriptorHash = Objects.hash(target.getType(), target.getEndpoint(), target.getBucket(), target.getCredentialRef());
        return credentialVersion + ":" + descriptorHash;
    }

    private String cacheKey(Organisation organisation, String targetName) {
        return organisation.getId() + ":" + targetName;
    }

    /** A cached target service tagged with the version that produced it (see {@link #versionFor}). */
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
