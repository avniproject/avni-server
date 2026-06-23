package org.avni.server.spike;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.S3Object;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Spike DR-2 (avni-server#1014): prove an AWS Java SDK v1 presigned GET signs against
 * Google Cloud Storage's S3-interoperability endpoint and downloads with a plain HTTP client.
 *
 * THROWAWAY. Not a shippable test. The deliverable is the go/no-go decision recorded on #1011.
 *
 * Mirrors {@code AWSMinioService}'s client construction. The ONLY differences for GCS:
 *   - endpoint            : https://storage.googleapis.com   (vs the Minio URL)
 *   - credentials         : GCS HMAC interop keys             (vs Minio access/secret)
 *   - signing region      : a placeholder ("auto")            (GCS ignores it; the V4 signer requires one)
 * Everything else (AWSS3V4SignerType, path-style, BasicAWSCredentials, generatePresignedUrl) is identical.
 *
 * Gated on env vars via Assume, so it SKIPS in CI when creds are absent. To run once the Avni-owned
 * GCS test bucket + HMAC key exist:
 *
 *   GCS_HMAC_ACCESS_KEY=...  GCS_HMAC_SECRET=...  GCS_BUCKET=avni-...-test  GCS_TEST_OBJECT_KEY=path/to/object \
 *   ./gradlew :avni-server-api:test --tests org.avni.server.spike.GcsPresignedGetSpikeTest --info
 *
 * Optional overrides: GCS_ENDPOINT (default https://storage.googleapis.com),
 *                     GCS_SIGNING_REGION (default "auto"),
 *                     GCS_FORWARDED_USER_NAME / GCS_FORWARDED_AUTH_TOKEN (defaults present)
 *                     to verify the production client's forwarded headers don't break the signature.
 */
public class GcsPresignedGetSpikeTest {

    private String accessKey;
    private String secret;
    private String bucket;
    private String objectKey;
    private String endpoint;
    private String signingRegion;
    private String forwardedUserName;
    private String forwardedAuthToken;

    private AmazonS3 gcs;

    @Before
    public void setUp() {
        accessKey = System.getenv("GCS_HMAC_ACCESS_KEY");
        secret = System.getenv("GCS_HMAC_SECRET");
        bucket = System.getenv("GCS_BUCKET");
        objectKey = System.getenv("GCS_TEST_OBJECT_KEY");
        // Without real GCS interop creds the spike cannot prove anything; skip rather than fail (CI-safe).
        Assume.assumeTrue(
                "Set GCS_HMAC_ACCESS_KEY, GCS_HMAC_SECRET, GCS_BUCKET, GCS_TEST_OBJECT_KEY to run this spike",
                isPresent(accessKey) && isPresent(secret) && isPresent(bucket) && isPresent(objectKey));

        endpoint = orDefault(System.getenv("GCS_ENDPOINT"), "https://storage.googleapis.com");
        signingRegion = orDefault(System.getenv("GCS_SIGNING_REGION"), "auto");
        forwardedUserName = orDefault(System.getenv("GCS_FORWARDED_USER_NAME"), "spike-user");
        forwardedAuthToken = orDefault(System.getenv("GCS_FORWARDED_AUTH_TOKEN"), "spike-token-placeholder");

        gcs = buildGcsInteropClient(endpoint, signingRegion, accessKey, secret);
        log("client built  endpoint=%s  signingRegion=%s  bucket=%s  key=%s", endpoint, signingRegion, bucket, objectKey);
    }

    /** Client construction copied from AWSMinioService, with GCS endpoint/region/credentials. */
    private AmazonS3 buildGcsInteropClient(String endpoint, String signingRegion, String accessKey, String secret) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setSignerOverride("AWSS3V4SignerType");
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, signingRegion))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfiguration)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secret)))
                .build();
    }

    /** AC: object `exists` behaviour on GCS interop (HEAD object). */
    @Test
    public void exists_returnsTrueForTheTestObject() {
        boolean exists;
        try {
            exists = gcs.doesObjectExist(bucket, objectKey);
        } catch (AmazonS3Exception e) {
            logS3Error("exists/HEAD", e);
            throw e;
        }
        log("exists(%s) = %b", objectKey, exists);
        assertTrue("doesObjectExist should return true for the seeded test object on GCS interop", exists);
    }

    /** AC: object `get` behaviour on GCS interop (GET object via SDK). */
    @Test
    public void get_streamsTheObjectContent() throws Exception {
        try (S3Object s3Object = gcs.getObject(bucket, objectKey);
             InputStream in = s3Object.getObjectContent()) {
            byte[] head = in.readNBytes(64);
            log("get() ok  contentLength=%d  firstBytes=%d", s3Object.getObjectMetadata().getContentLength(), head.length);
            assertTrue("getObject should stream content", head.length > 0);
        } catch (AmazonS3Exception e) {
            logS3Error("get/GET", e);
            throw e;
        }
    }

    /** AC: the core proof — AWS-SDK presigned GET signs against GCS and downloads over plain HTTP. */
    @Test
    public void presignedGet_signsAgainstGcsAndDownloads() throws Exception {
        Date expiry = new Date(System.currentTimeMillis() + 15 * 60 * 1000);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiry);
        URL url = gcs.generatePresignedUrl(request);
        assertNotNull("presigned URL should be generated", url);

        // Printed so it can be copy-pasted into `curl` AND handed to the client team for the
        // RNFetchBlob-on-Android leg of the AC (302 -> GET, with forwarded headers present).
        log("PRESIGNED GET URL (valid ~15m):%n%s", url);

        // Download with a plain HTTP client. Forwarded USER-NAME / AUTH-TOKEN headers are set to prove
        // they do NOT break the signature (GCS validates only its own signed headers; host is the only
        // signed header in a default SigV4 query presign).
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true); // real client follows a 302 to this URL; harmless here
        conn.setRequestProperty("USER-NAME", forwardedUserName);
        conn.setRequestProperty("AUTH-TOKEN", forwardedAuthToken);
        conn.connect();

        int status = conn.getResponseCode();
        long downloaded = 0;
        if (status >= 400) {
            // GCS returns an XML error body with the real reason (<Code>SignatureDoesNotMatch</Code> vs
            // <Code>AccessDenied</Code>) — print it so signing-vs-permission is unambiguous.
            String body = readBody(conn.getErrorStream());
            log("download FAILED status=%d  GCS error body:%n%s", status, body);
        } else {
            try (InputStream in = conn.getInputStream()) {
                downloaded = drain(in);
            }
            log("download status=%d  bytes=%d  (forwarded headers present: USER-NAME, AUTH-TOKEN)", status, downloaded);
        }

        assertEquals("presigned GET against GCS should return 200 with forwarded headers present", 200, status);
        assertTrue("downloaded body should be non-empty", downloaded > 0);
    }

    private long drain(InputStream in) throws Exception {
        if (in == null) return 0;
        long total = 0;
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) total += n;
        return total;
    }

    private String readBody(InputStream in) throws Exception {
        if (in == null) return "(no body)";
        try (InputStream s = in) {
            return new String(s.readAllBytes());
        }
    }

    /**
     * Surfaces the real GCS reason behind an SDK exception. The AWS SDK often can't map GCS's XML
     * error into errorCode (you see a bare "403 Forbidden" with null Request ID), so the raw response
     * XML — which carries {@code <Code>AccessDenied</Code>} (permission) vs
     * {@code <Code>SignatureDoesNotMatch</Code>} (signing/interop) — is the disambiguator.
     */
    private static void logS3Error(String op, AmazonS3Exception e) {
        log("%s FAILED  httpStatus=%d  sdkErrorCode=%s  message=%s", op, e.getStatusCode(), e.getErrorCode(), e.getErrorMessage());
        log("%s GCS error XML:%n%s", op, e.getErrorResponseXml() == null ? "(none)" : e.getErrorResponseXml());
        if (e.getAdditionalDetails() != null && !e.getAdditionalDetails().isEmpty()) {
            log("%s additional details: %s", op, e.getAdditionalDetails());
        }
    }

    private static boolean isPresent(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String orDefault(String value, String fallback) {
        return isPresent(value) ? value : fallback;
    }

    private static void log(String fmt, Object... args) {
        System.out.println("[GCS-SPIKE] " + String.format(fmt, args));
    }
}
