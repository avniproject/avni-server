package org.avni.server.framework.context;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.config.CognitoConfig;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.*;
import org.avni.server.web.AuthDetailsController;
import org.keycloak.OAuth2Constants;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;

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

    @Autowired
    public DeploymentSpecificConfiguration(SpringProfiles springProfiles, OrganisationConfigService organisationConfigService) {
        this.springProfiles = springProfiles;
        this.organisationConfigService = organisationConfigService;
    }

    @Profile({"dev", "staging"})
    @Bean("S3Service")
    @Primary
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public S3Service getProxiedS3Service() {
        User user = UserContextHolder.getUser();
        Organisation organisation = UserContextHolder.getOrganisation();
        boolean isMinioConfiguredOrgUser = false;
        if (user != null && organisation != null) {
            OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(organisation);
            Boolean useMinioForStorage = (Boolean) organisationConfig.getConfigValue(OrganisationConfigSettingKey.useMinioForStorage);
            if (useMinioForStorage != null && useMinioForStorage) {
                isMinioConfiguredOrgUser = true;
            }
        }

        if (isMinioConfiguredOrgUser && awsMinioService != null)
            return awsMinioService;

        return getBatchS3Service();
    }

    @Profile({"!dev", "!staging"})
    @Bean("S3Service")
    @Primary
    public S3Service getRegularS3Service() {
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

        // TODO(avniproject/avni-server#1012): per-org/per-data-class resolver (story 3) selects the GCS
        //  backend for MODEL data; until then GCS is wired only as a last-resort DEFAULT provider so the
        //  bean is selectable when gcs.s3.enable=true and AWS/MinIO are disabled.
        if (gcsStorageService != null)
            return gcsStorageService;

        throw new NoSuchBeanDefinitionException("BatchS3Service", "Batch Storage service bean of type BatchS3Service not found");
    }
}
