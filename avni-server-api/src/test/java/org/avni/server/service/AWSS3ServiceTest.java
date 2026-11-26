package org.avni.server.service;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AWSS3ServiceTest {
    private static final String PRIMARY_URL = "https://s3.ap-south-1.amazonaws.com/prod-user-media/goonj/a.jpg";

    @Before
    public void setUp() {
        Organisation organisation = new TestOrganisationBuilder().build();
        organisation.setMediaDirectory("goonj");
        UserContextHolder.create(new UserContextBuilder().withOrganisation(organisation).build());
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private AWSS3Service service(String bucket, String region, List<String> aliases) {
        return new AWSS3Service(bucket, "AKIAEXAMPLE", "secret", false, false, region, aliases, Collections.emptyList());
    }

    @Test
    public void signsForConfiguredBucketAndRegion() {
        URL url = service("prod-user-media", "ap-south-1", Collections.emptyList()).generateMediaDownloadUrl(PRIMARY_URL);
        assertEquals("s3.ap-south-1.amazonaws.com", url.getHost());
        assertEquals("/prod-user-media/goonj/a.jpg", url.getPath());
    }

    @Test
    public void readsAliasedBucketUrlsFromTheDrReplicaInARegionNewerThanTheSdk() {
        URL url = service("prod-user-media-dr-hyderabad", "ap-south-2", List.of("prod-user-media"))
                .generateMediaDownloadUrl(PRIMARY_URL);
        assertEquals("s3.ap-south-2.amazonaws.com", url.getHost());
        assertEquals("/prod-user-media-dr-hyderabad/goonj/a.jpg", url.getPath());
    }

    @Test(expected = AccessDeniedException.class)
    public void rejectsOtherBucketsWithoutAnAlias() {
        service("prod-user-media-dr-hyderabad", "ap-south-2", Collections.emptyList()).generateMediaDownloadUrl(PRIMARY_URL);
    }

    @Test(expected = AccessDeniedException.class)
    public void rejectsBucketsThatAreNotAliased() {
        service("prod-user-media-dr-hyderabad", "ap-south-2", List.of("prod-user-media"))
                .generateMediaDownloadUrl("https://s3.ap-south-1.amazonaws.com/someone-elses-bucket/goonj/a.jpg");
    }

    @Test(expected = AccessDeniedException.class)
    public void rejectsOtherOrganisationsMediaDirectory() {
        service("prod-user-media-dr-hyderabad", "ap-south-2", List.of("prod-user-media"))
                .generateMediaDownloadUrl("https://s3.ap-south-1.amazonaws.com/prod-user-media/other-org/a.jpg");
    }
}
