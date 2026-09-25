package org.avni.server.service.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StorageLastModifiedTest {
    private static final Date LAST_MODIFIED = new Date(1_700_000_000_000L);
    private static final String OBJECT_KEY = "orgdir/MobileDbBackup-cat-uuid";

    @Before
    public void setUp() {
        Organisation organisation = new TestOrganisationBuilder().setId(1L).build();
        organisation.setMediaDirectory("orgdir");
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private TargetStorageService service(AmazonS3 client) {
        return new TargetStorageService("bucket", client, false, false, Collections.emptyList());
    }

    @Test
    public void anExistingObjectReportsWhenItWasLastWritten() {
        AmazonS3 client = mock(AmazonS3.class);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setLastModified(LAST_MODIFIED);
        when(client.getObjectMetadata("bucket", OBJECT_KEY)).thenReturn(metadata);

        assertEquals(Optional.of(LAST_MODIFIED), service(client).getLastModified("MobileDbBackup-cat-uuid"));
    }

    @Test
    public void aMissingObjectIsEmptyJustAsFileExistsIsFalse() {
        AmazonS3 client = mock(AmazonS3.class);
        AmazonS3Exception notFound = new AmazonS3Exception("Not Found");
        notFound.setStatusCode(404);
        when(client.getObjectMetadata(anyString(), anyString())).thenThrow(notFound);
        when(client.doesObjectExist(anyString(), anyString())).thenReturn(false);

        assertEquals(Optional.empty(), service(client).getLastModified("MobileDbBackup-cat-uuid"));
        assertFalse(service(client).fileExists("MobileDbBackup-cat-uuid"));
    }

    @Test
    public void anUnreadableObjectIsEmptyRatherThanAThrow() {
        // The fast sync download path must not 500 because a HEAD was refused.
        AmazonS3 client = mock(AmazonS3.class);
        when(client.getObjectMetadata(anyString(), anyString()))
                .thenThrow(new AmazonS3Exception("AccessDenied"));

        assertEquals(Optional.empty(), service(client).getLastModified("MobileDbBackup-cat-uuid"));
    }
}
