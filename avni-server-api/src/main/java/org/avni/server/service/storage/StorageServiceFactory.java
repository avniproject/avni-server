package org.avni.server.service.storage;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.avni.server.domain.Organisation;
import org.avni.server.service.S3Service;
import org.avni.server.service.media.MediaUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Builds a concrete {@link S3Service} for a named storage target descriptor
 * (avniproject/avni-server#1012). Generalises the per-backend client construction from
 * {@code AWSS3Service}/{@code AWSMinioService}/{@code GCSStorageService} (which are global-prop
 * singletons) so a per-org target can have its OWN endpoint/bucket/credentials.
 * <p>
 * Resolution: target descriptor (non-secret, from org config) + credentials (resolved via
 * {@link StorageCredentialProvider}, P0 = encrypted-per-org DB). The GCS client-build mirrors the
 * Story-2 {@code GCSStorageService} (S3-interop endpoint, path-style, {@code AWSS3V4SignerType}).
 */
@Component
public class StorageServiceFactory {
    private static final Regions SIGNING_REGION = Regions.AP_SOUTH_1;

    private final StorageCredentialProvider credentialProvider;
    private final boolean s3InDev;
    private final Boolean isDev;
    private final List<MediaUrlResolver> mediaUrlResolvers;

    @Autowired
    public StorageServiceFactory(StorageCredentialProvider credentialProvider,
                                 @Value("${avni.connectToS3InDev}") boolean s3InDev,
                                 Boolean isDev,
                                 List<MediaUrlResolver> mediaUrlResolvers) {
        this.credentialProvider = credentialProvider;
        this.s3InDev = s3InDev;
        this.isDev = isDev;
        this.mediaUrlResolvers = mediaUrlResolvers;
    }

    public S3Service build(Organisation organisation, StorageTarget target) {
        StorageTargetCredentials credentials = credentialProvider.getCredentials(organisation, target.getCredentialRef());
        AmazonS3 s3Client = buildClient(target, credentials);
        return new TargetStorageService(target.getBucket(), s3Client, s3InDev, isDev, mediaUrlResolvers);
    }

    private AmazonS3 buildClient(StorageTarget target, StorageTargetCredentials credentials) {
        AWSStaticCredentialsProvider credentialsProvider =
                new AWSStaticCredentialsProvider(new BasicAWSCredentials(credentials.getAccessKey(), credentials.getSecretKey()));
        switch (target.getType()) {
            case S3:
                // Honor an explicit endpoint (path-style, e.g. a non-AWS S3-compatible host) when
                // provided; otherwise fall back to the default region.
                if (StringUtils.hasText(target.getEndpoint())) {
                    return AmazonS3ClientBuilder.standard()
                            .withEndpointConfiguration(new AwsClientBuilder
                                    .EndpointConfiguration(target.getEndpoint(), SIGNING_REGION.getName()))
                            .withPathStyleAccessEnabled(true)
                            .withCredentials(credentialsProvider)
                            .build();
                }
                return AmazonS3ClientBuilder.standard()
                        .withRegion(SIGNING_REGION)
                        .withPathStyleAccessEnabled(true)
                        .withCredentials(credentialsProvider)
                        .build();
            case MINIO:
            case GCS:
                if (!StringUtils.hasText(target.getEndpoint())) {
                    throw new StorageConfigurationException(String.format(
                            "Storage target '%s' of type %s requires an 'endpoint'", target.getName(), target.getType()));
                }
                ClientConfiguration clientConfiguration = new ClientConfiguration();
                clientConfiguration.setSignerOverride("AWSS3V4SignerType");
                return AmazonS3ClientBuilder.standard()
                        .withEndpointConfiguration(new AwsClientBuilder
                                .EndpointConfiguration(target.getEndpoint(), SIGNING_REGION.getName()))
                        .withPathStyleAccessEnabled(true)
                        .withClientConfiguration(clientConfiguration)
                        .withCredentials(credentialsProvider)
                        .build();
            default:
                throw new StorageConfigurationException("Unsupported storage target type: " + target.getType());
        }
    }
}
