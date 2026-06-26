package org.avni.server.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.avni.server.domain.UserContext;
import org.avni.server.service.media.MediaUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;

import static java.lang.String.format;

// Google Cloud Storage backend via S3 interoperability (path-style, AWSS3V4SignerType, GCS HMAC interop credentials).
@Service("GCSStorageService")
@ConditionalOnProperty(value = "gcs.s3.enable", havingValue = "true")
public class GCSStorageService extends StorageService {
    private static final Logger logger = LoggerFactory.getLogger(GCSStorageService.class);

    @Autowired
    public GCSStorageService(@Value("${avni.bucketName}") String bucketName,
                             @Value("${gcs.endpoint}") String gcsEndpoint,
                             @Value("${gcs.accessKey}") String gcsAccessKey,
                             @Value("${gcs.secretAccessKey}") String gcsSecretAccessKey,
                             @Value("${avni.connectToS3InDev}") boolean s3InDev, Boolean isDev,
                             List<MediaUrlResolver> mediaUrlResolvers) {
        super(bucketName, s3InDev, logger, isDev, mediaUrlResolvers);
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");
        s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder
                        .EndpointConfiguration(gcsEndpoint, REGION.getName()))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(
                        new AWSStaticCredentialsProvider(new BasicAWSCredentials(gcsAccessKey, gcsSecretAccessKey)))
                .build();
    }

    @Override
    public InputStream getObjectContentFromUrl(String s3url) {
        String objectKey = getObjectKeyFromUrl(s3url);
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException(format("Invalid GCS URL: no object key: %s", s3url));
        }
        return getObjectContent(objectKey);
    }

    @Override
    public URL generateMediaDownloadUrl(String url) {
        UserContext userContext = authorizeUser();
        String mediaDirectory = getOrgDirectoryName();

        String objectKey = getObjectKeyFromUrl(url);
        if (objectKey == null || objectKey.isEmpty()) {
            String message = format("User '%s' not authorized to access '%s'", userContext.getUserName(), url);
            throw new AccessDeniedException(message);
        }
        Matcher matcher = mediaDirPattern.matcher(objectKey);
        String mediaDirectoryFromUrl = null;
        if (matcher.find()) {
            mediaDirectoryFromUrl = matcher.group("mediaDir");
        }
        if (!mediaDirectory.equals(mediaDirectoryFromUrl) || !(bucketName.equals(getBucketFromUrl(url)))) {
            String message = format("User '%s' not authorized to access '%s'", userContext.getUserName(), url);
            throw new AccessDeniedException(message);
        }

        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(HttpMethod.GET).withExpiration(getExpireDate(DOWNLOAD_EXPIRY_DURATION));
        return s3Client.generatePresignedUrl(generatePresignedUrlRequest);
    }

    // Object key from a path-style URL: https://<endpoint>/<bucket>/<key>
    static String getObjectKeyFromUrl(String url) {
        String path = rawPathOf(url);
        int index = path.indexOf('/', 1);
        if (index == -1 || index == path.length() - 1) {
            return null;
        }
        return decode(path.substring(index + 1));
    }

    static String getBucketFromUrl(String url) {
        String path = rawPathOf(url);
        int index = path.indexOf('/', 1);
        if (index == -1) {
            return decode(path.substring(1));
        }
        return decode(path.substring(1, index));
    }

    // Raw (still percent-encoded) path. preprocessUrlStr keeps escapes intact so each segment decodes once to the exact stored key.
    static String rawPathOf(String url) {
        URI uri = URI.create(preprocessUrlStr(url));
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException(format("Invalid GCS URL: no path: %s", url));
        }
        return path;
    }

    // URL-encode the whole string, then restore the ':' and '/' path delimiters so URI.create accepts it.
    static String preprocessUrlStr(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8")
                    .replace("%3A", ":")
                    .replace("%2F", "/")
                    .replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String decode(String str) {
        return URLDecoder.decode(str, StandardCharsets.UTF_8);
    }
}
