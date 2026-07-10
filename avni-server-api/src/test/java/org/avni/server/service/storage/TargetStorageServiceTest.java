package org.avni.server.service.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// A routed GCS failure must be logged (getErrorResponseXml) AND still propagate — these assert it is not swallowed.
public class TargetStorageServiceTest {

    private TargetStorageService service(AmazonS3 client) {
        return new TargetStorageService("bucket", client, false, false, Collections.emptyList());
    }

    @Test
    public void getObjectContentRethrowsAmazonS3Exception() {
        AmazonS3 client = mock(AmazonS3.class);
        when(client.getObject(anyString(), anyString())).thenThrow(new AmazonS3Exception("AccessDenied"));
        assertThrows(AmazonS3Exception.class, () -> service(client).getObjectContent("models/x.bin"));
    }

    @Test
    public void putObjectRethrowsAmazonS3Exception() {
        AmazonS3 client = mock(AmazonS3.class);
        when(client.putObject(any(PutObjectRequest.class))).thenThrow(new AmazonS3Exception("AccessDenied"));
        assertThrows(AmazonS3Exception.class, () -> service(client).putObject("models/x.bin", new File("/tmp/irrelevant")));
    }
}
