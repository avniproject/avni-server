package org.avni.server.util;

import org.avni.server.builder.OrganisationBuilder;
import org.avni.server.domain.Organisation;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class S3FileTest {
    Organisation organisation;

    @Before
    public void setup() {
        organisation = new OrganisationBuilder().withMediaPath("example").build();
    }

    @Test
    public void testGetFilePathRelativeToExtension() {
        S3File s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.Extensions);
        assertEquals("foo.txt", s3File.getFilePathRelativeToExtension());
    }

    @Test
    public void testGetExtensionFilePathRelativeToOrganisation() {
        S3File s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.Extensions);
        assertEquals("extensions/foo.txt", s3File.getExtensionFilePathRelativeToOrganisation());
    }

    @Test
    public void testGetPath() {
        S3File s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.Main);
        assertEquals("example/foo.txt", s3File.getPath());

        s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.Extensions);
        assertEquals("example/extensions/foo.txt", s3File.getPath());

        s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.Export);
        assertEquals("exports/example/foo.txt", s3File.getPath());

        s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.BulkUploadsInput);
        assertEquals("bulkuploads/input/example/foo.txt", s3File.getPath());

        s3File = S3File.organisationFile(organisation, "foo.txt", S3FileType.BulkUploadsError);
        assertEquals("bulkuploads/error/example/foo.txt", s3File.getPath());
    }

    @Test
    public void organisationFileFromFullPath() {
        S3File s3File = S3File.organisationFileFromFullPath(organisation, "example/foo.txt", S3FileType.Main);
        assertEquals("example/foo.txt", s3File.getPath());

        s3File = S3File.organisationFileFromFullPath(organisation, "bulkuploads/error/example/foo.txt", S3FileType.BulkUploadsError);
        assertEquals("bulkuploads/error/example/foo.txt", s3File.getPath());
    }
}
