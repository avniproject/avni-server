package org.avni.server.service.storage;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.avni.server.domain.UserContext;
import org.avni.server.service.StorageService;
import org.avni.server.service.media.MediaUrlResolver;
import org.avni.server.util.MinioUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;

import static java.lang.String.format;

// A StorageService built per-org from a StorageTarget descriptor + resolved credentials, carrying its own bucket and S3 client.
public class TargetStorageService extends StorageService {
    private static final Logger logger = LoggerFactory.getLogger(TargetStorageService.class);

    public TargetStorageService(String bucketName, AmazonS3 s3Client, boolean s3InDev, Boolean isDev,
                                List<MediaUrlResolver> mediaUrlResolvers) {
        super(bucketName, s3InDev, logger, isDev, mediaUrlResolvers);
        this.s3Client = s3Client;
    }

    // Releases the S3 client's connection pool; called when a cached target service is superseded.
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
    public InputStream getObjectContent(String s3Key) {
        try {
            return super.getObjectContent(s3Key);
        } catch (AmazonS3Exception e) {
            logS3Error("getObjectContent", s3Key, e);
            throw e;
        }
    }

    @Override
    public String putObject(String objectKey, File tempFile) {
        try {
            return super.putObject(objectKey, tempFile);
        } catch (AmazonS3Exception e) {
            logS3Error("putObject", objectKey, e);
            throw e;
        }
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
        try {
            return s3Client.generatePresignedUrl(generatePresignedUrlRequest);
        } catch (AmazonS3Exception e) {
            logS3Error("generateMediaDownloadUrl", objectKey, e);
            throw e;
        }
    }
}
