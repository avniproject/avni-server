package org.avni.server.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Pure unit tests for the GCS path-style URL parsing helpers in {@link GCSStorageService}.
 * These exercise {@code preprocessUrlStr} / {@code getBucketFromUrl} / {@code getObjectKeyFromUrl}
 * without constructing the service (which would build a live S3 client). The helpers were made
 * package-private static for this reason.
 * <p>
 * NB: a live-GCS / presign test is intentionally out of scope (covered by the #1014 spike).
 */
public class GCSStorageServiceTest {

    private static final String ENDPOINT = "https://storage.googleapis.com";
    private static final String BUCKET = "my-bucket";

    // --- preprocessUrlStr round-trip ---------------------------------------------------------

    @Test
    public void preprocessUrlStrIsParseableByUri() {
        // A plain models/<sha256>.bin key must survive preprocessing and parse out cleanly.
        String key = "models/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd.bin";
        String url = ENDPOINT + "/" + BUCKET + "/" + key;

        assertEquals(key, GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }

    @Test
    public void preprocessUrlStrPreservesColonAndSlashDelimiters() {
        String key = "models/x.bin";
        String url = ENDPOINT + "/" + BUCKET + "/" + key;
        String preprocessed = GCSStorageService.preprocessUrlStr(url);

        // scheme colon and all path slashes must be restored so URI.create accepts it
        assertEquals("https://storage.googleapis.com/my-bucket/models/x.bin", preprocessed);
    }

    @Test
    public void keyWithSpacesRoundTrips() {
        String key = "media/orgdir/some file with spaces.png";
        String url = ENDPOINT + "/" + BUCKET + "/" + key;

        // spaces are mapped to %20 by preprocessing, then decoded back to the exact stored key
        assertEquals(key, GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }

    @Test
    public void keyWithSpecialCharsRoundTrips() {
        // '+', '&', '=', '#' and unicode in the key must survive the encode/decode round-trip
        String key = "media/orgdir/a+b & c=d #1 é.png";
        String url = ENDPOINT + "/" + BUCKET + "/" + key;

        assertEquals(key, GCSStorageService.getObjectKeyFromUrl(url));
    }

    // --- bucket + key extraction -------------------------------------------------------------

    @Test
    public void extractsBucketAndKeyFromPathStyleUrl() {
        String url = ENDPOINT + "/" + BUCKET + "/media/orgdir/file.png";

        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
        assertEquals("media/orgdir/file.png", GCSStorageService.getObjectKeyFromUrl(url));
    }

    @Test
    public void extractsNestedKeyWithMultipleSegments() {
        String url = ENDPOINT + "/" + BUCKET + "/a/b/c/d.bin";
        assertEquals("a/b/c/d.bin", GCSStorageService.getObjectKeyFromUrl(url));
    }

    // --- null / empty key guard (NPE prevention behind the AccessDeniedException) ------------

    @Test
    public void bucketOnlyUrlYieldsNullKeyNotNpe() {
        // https://<endpoint>/<bucket> with no key: parser must return null (drives AccessDeniedException,
        // never an NPE) and still recover the bucket.
        String url = ENDPOINT + "/" + BUCKET;
        assertNull(GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }

    @Test
    public void trailingSlashBucketUrlYieldsNullKeyNotNpe() {
        // https://<endpoint>/<bucket>/ trailing slash: empty key segment -> null, no NPE.
        String url = ENDPOINT + "/" + BUCKET + "/";
        assertNull(GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }
}
