package org.avni.server.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GCSStorageServiceTest {

    private static final String ENDPOINT = "https://storage.googleapis.com";
    private static final String BUCKET = "my-bucket";

    @Test
    public void preprocessUrlStrIsParseableByUri() {
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

        assertEquals("https://storage.googleapis.com/my-bucket/models/x.bin", preprocessed);
    }

    @Test
    public void keyWithSpacesRoundTrips() {
        String key = "media/orgdir/some file with spaces.png";
        String url = ENDPOINT + "/" + BUCKET + "/" + key;

        assertEquals(key, GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }

    @Test
    public void keyWithSpecialCharsRoundTrips() {
        String key = "media/orgdir/a+b & c=d #1 é.png";
        String url = ENDPOINT + "/" + BUCKET + "/" + key;

        assertEquals(key, GCSStorageService.getObjectKeyFromUrl(url));
    }

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

    @Test
    public void bucketOnlyUrlYieldsNullKeyNotNpe() {
        String url = ENDPOINT + "/" + BUCKET;
        assertNull(GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }

    @Test
    public void trailingSlashBucketUrlYieldsNullKeyNotNpe() {
        String url = ENDPOINT + "/" + BUCKET + "/";
        assertNull(GCSStorageService.getObjectKeyFromUrl(url));
        assertEquals(BUCKET, GCSStorageService.getBucketFromUrl(url));
    }
}
