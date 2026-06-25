package org.avni.server.service.storage;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.avni.server.domain.UserContext;
import org.avni.server.service.StorageService;
import org.avni.server.service.media.MediaUrlResolver;
import org.avni.server.util.MinioUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;

import static java.lang.String.format;

/**
 * A {@link StorageService} built per-org from a {@link StorageTarget} descriptor + resolved
 * credentials (avniproject/avni-server#1012) - as opposed to the startup-singleton
 * {@code AWSS3Service}/{@code AWSMinioService}/{@code GCSStorageService} that are built from global
 * {@code @Value} props. It carries its own bucket and a pre-built {@link AmazonS3} client (so a
 * target can point at its own endpoint/bucket/creds).
 * <p>
 * URL parsing reuses {@link MinioUri} (path-style, which all three S3-interop backends use here).
 * The download-url authorisation (mediaDir == org dir, bucket match) mirrors {@code AWSMinioService}.
 */
public class TargetStorageService extends StorageService {
    private static final Logger logger = LoggerFactory.getLogger(TargetStorageService.class);

    public TargetStorageService(String bucketName, AmazonS3 s3Client, boolean s3InDev, Boolean isDev,
                                List<MediaUrlResolver> mediaUrlResolvers) {
        super(bucketName, s3InDev, logger, isDev, mediaUrlResolvers);
        this.s3Client = s3Client;
    }

    /**
     * Releases the underlying {@link AmazonS3} client's connection pool. Called by the
     * {@link StorageResolver} when a cached target service is superseded (credential/target rotation),
     * so a rebuilt client does not leak the previous one's connections (avniproject/avni-server#1012).
     */
    public void shutdown() {
        try {
            s3Client.shutdown();
        } catch (RuntimeException e) {
            logger.warn("Failed to shutdown superseded storage client; continuing", e);
        }
    }

    @Override
    public InputStream getObjectContentFromUrl(String s3url) {
        MinioUri minioUri = new MinioUri(s3url);
        return getObjectContent(minioUri.getKey());
    }

    @Override
    public URL generateMediaDownloadUrl(String url) {
        UserContext userContext = authorizeUser();
        String mediaDirectory = getOrgDirectoryName();

        MinioUri minioUri = new MinioUri(url);
        String objectKey = minioUri.getKey();
        Matcher matcher = mediaDirPattern.matcher(objectKey);
        String mediaDirectoryFromUrl = null;
        if (matcher.find()) {
            mediaDirectoryFromUrl = matcher.group("mediaDir");
        }
        if (!mediaDirectory.equals(mediaDirectoryFromUrl) || !(bucketName.equals(minioUri.getBucket()))) {
            String message = format("User '%s' not authorized to access '%s'", userContext.getUserName(), url);
            throw new AccessDeniedException(message);
        }

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.GET).withExpiration(getExpireDate(DOWNLOAD_EXPIRY_DURATION));
        return s3Client.generatePresignedUrl(generatePresignedUrlRequest);
    }
}
