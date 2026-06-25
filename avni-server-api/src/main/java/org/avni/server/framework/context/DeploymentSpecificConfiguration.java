package org.avni.server.framework.context;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.*;
import org.avni.server.service.storage.StorageResolver;
import org.avni.server.service.storage.StorageServiceProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;

import java.util.function.Supplier;

@Configuration
public class DeploymentSpecificConfiguration {
    @Qualifier("AWSS3Service")
    @Autowired(required = false)
    private AWSS3Service awss3Service;

    @Qualifier("AWSMinioService")
    @Autowired(required = false)
    private AWSMinioService awsMinioService;

    @Qualifier("GCSStorageService")
    @Autowired(required = false)
    private GCSStorageService gcsStorageService;

    private final SpringProfiles springProfiles;

    private final OrganisationConfigService organisationConfigService;

    private final StorageResolver storageResolver;

    @Autowired
    public DeploymentSpecificConfiguration(SpringProfiles springProfiles,
                                           OrganisationConfigService organisationConfigService,
                                           StorageResolver storageResolver) {
        this.springProfiles = springProfiles;
        this.organisationConfigService = organisationConfigService;
        this.storageResolver = storageResolver;
    }

    /**
     * The per-org, per-data-class storage seam (avniproject/avni-server#1012). A PLAIN SINGLETON
     * (active in all profiles) - NOT request-scoped: Story 4's media upload/serve must work in
     * batch/sync/non-request contexts too, where a request-scoped bean would throw
     * {@code ScopeNotActiveException}. The current org is read per-call from {@link UserContextHolder}
     * (a thread-local, valid on request threads); when no org/active context is present the resolver
     * falls back to the default backend (D16). Routes MODEL to the org's MODEL backend when an org is
     * present; DEFAULT data resolves to today's backend, byte-for-byte unchanged.
     */
    @Bean("StorageServiceProvider")
    @Primary
    public StorageServiceProvider getStorageServiceProvider() {
        return new StorageServiceProvider(storageResolver, this::resolveDefaultBackend);
    }

    /**
     * The request-scoped {@code S3Service} (DEFAULT data class) for dev/staging, where the default
     * backend is selected per-request from the current org's {@code useMinioForStorage} toggle - so
     * the selection must be re-evaluated on every request (hence request scope + a scoped proxy so it
     * is injectable into singletons). It resolves through the per-org resolver, but for the DEFAULT
     * class the resolver returns the default backend, so existing media/extension/export/bulk-upload
     * call sites behave exactly as before (D16).
     * <p>
     * Note: batch/sync/non-request consumers must NOT route through this request-scoped bean (they
     * would hit {@code ScopeNotActiveException} with no active request); they inject
     * {@code @Qualifier("BatchS3Service")} instead, which is a plain singleton (D16).
     */
    @Profile({"dev", "staging"})
    @Bean("S3Service")
    @Primary
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public S3Service getProxiedS3Service() {
        return storageResolver.resolve(UserContextHolder.getOrganisation(), StorageDataClass.DEFAULT, this::resolveDefaultBackend);
    }

    /**
     * The plain-singleton {@code S3Service} (DEFAULT data class) for non-dev/staging profiles
     * (prod/test). Outside dev/staging the default backend is a static, profile-derived choice (no
     * per-request minio toggle), so a singleton is correct and - crucially - usable from
     * batch/sync/non-request contexts without an active request. It resolves through the per-org
     * resolver, but for the DEFAULT class the resolver returns the default backend, so existing call
     * sites behave byte-for-byte as before (D16).
     */
    @Profile({"!dev", "!staging"})
    @Bean("S3Service")
    @Primary
    public S3Service getRegularS3Service() {
        return storageResolver.resolve(UserContextHolder.getOrganisation(), StorageDataClass.DEFAULT, this::resolveDefaultBackend);
    }

    /**
     * Today's default-backend selection, made profile-agnostic. Replicates the previous dev/staging
     * {@code useMinioForStorage} branch (so DEFAULT is byte-for-byte unchanged there) and falls back
     * to the batch service everywhere else (prod). Used as the resolver's DEFAULT supplier.
     */
    private S3Service resolveDefaultBackend() {
        if (springProfiles.isDevOrStaging()) {
            User user = UserContextHolder.getUser();
            Organisation organisation = UserContextHolder.getOrganisation();
            if (user != null && organisation != null) {
                OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
                Boolean useMinioForStorage = (Boolean) organisationConfig.getConfigValue(OrganisationConfigSettingKey.useMinioForStorage);
                if (useMinioForStorage != null && useMinioForStorage && awsMinioService != null) {
                    return awsMinioService;
                }
            }
        }
        return getBatchS3Service();
    }

    @Bean("BatchS3Service")
    public S3Service getBatchS3Service() {
        if (springProfiles.isOnPremise() && awsMinioService != null)
            return awsMinioService;

        if (awss3Service != null)
            return awss3Service;

        if (awsMinioService != null)
            return awsMinioService;

        // GCS is selectable as a last-resort DEFAULT provider when AWS/MinIO are disabled (e.g. an
        // org running entirely on GCS). Per-data-class MODEL routing to a per-org GCS target is
        // handled by the StorageResolver / StorageServiceProvider above.
        if (gcsStorageService != null)
            return gcsStorageService;

        throw new NoSuchBeanDefinitionException("BatchS3Service", "Batch Storage service bean of type BatchS3Service not found");
    }
}
