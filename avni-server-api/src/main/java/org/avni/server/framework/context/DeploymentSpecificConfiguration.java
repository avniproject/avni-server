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

    // Plain singleton (not request-scoped) so media routing also works in batch/sync/non-request contexts.
    @Bean("StorageServiceProvider")
    @Primary
    public StorageServiceProvider getStorageServiceProvider() {
        return new StorageServiceProvider(storageResolver, this::resolveDefaultBackend);
    }

    // dev/staging: request-scoped because the default backend depends on the per-request useMinioForStorage toggle.
    @Profile({"dev", "staging"})
    @Bean("S3Service")
    @Primary
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public S3Service getProxiedS3Service() {
        return storageResolver.resolve(UserContextHolder.getOrganisation(), StorageDataClass.DEFAULT, this::resolveDefaultBackend);
    }

    // prod/test: plain singleton (static default backend, usable without an active request).
    @Profile({"!dev", "!staging"})
    @Bean("S3Service")
    @Primary
    public S3Service getRegularS3Service() {
        return storageResolver.resolve(UserContextHolder.getOrganisation(), StorageDataClass.DEFAULT, this::resolveDefaultBackend);
    }

    // Today's default-backend selection, made profile-agnostic: dev/staging useMinioForStorage branch, else batch service.
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

        // last-resort default provider when AWS/MinIO are disabled
        if (gcsStorageService != null)
            return gcsStorageService;

        throw new NoSuchBeanDefinitionException("BatchS3Service", "Batch Storage service bean of type BatchS3Service not found");
    }
}
