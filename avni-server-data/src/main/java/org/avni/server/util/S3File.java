package org.avni.server.util;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;

import java.util.HashMap;
import java.util.Map;

public class S3File {
    private final String path;
    private final S3FileType fileType;
    private final String organisationMediaDirectory;

    private static final Map<S3FileType, String> filePathFormat = new HashMap<>();

    private static final String BULK_UPLOADS = "bulkuploads";

    static {
        filePathFormat.put(S3FileType.Main, "%s/%s");
        filePathFormat.put(S3FileType.Extensions, "%s/" + OrganisationConfig.Extension.EXTENSION_DIR + "/%s");
        filePathFormat.put(S3FileType.Global, "%s");
        filePathFormat.put(S3FileType.BulkUploadsError, BULK_UPLOADS + "/error/%s/%s");
        filePathFormat.put(S3FileType.BulkUploadsInput, BULK_UPLOADS + "/input/%s/%s");
        filePathFormat.put(S3FileType.Export, "exports/%s/%s");
    }

    public static S3File organisationFileFromFullPath(Organisation organisation, String fullPath, S3FileType s3FileType) {
        String pathFormat = filePathFormat.get(s3FileType);
        String path = String.format(pathFormat, organisation.getMediaDirectory(), "");
        return S3File.organisationFile(organisation, fullPath.replace(path, ""), s3FileType);
    }

    public static S3File organisationFile(Organisation organisation, String path, S3FileType s3FileType) {
        return new S3File(path, s3FileType, organisation.getMediaDirectory());
    }

    private S3File(String path, S3FileType fileType, String organisationMediaDirectory) {
        this.path = path;
        this.organisationMediaDirectory = organisationMediaDirectory;
        this.fileType = fileType;
    }

    public String getFilePathRelativeToExtension() {
        return getExtensionFilePathRelativeToOrganisation().replace(OrganisationConfig.Extension.EXTENSION_DIR + "/", "");
    }

    public String getExtensionFilePathRelativeToOrganisation() {
        if (fileType.equals(S3FileType.Extensions))
            return String.format(filePathFormat.get(S3FileType.Extensions), "", this.path).substring(1);
        throw new RuntimeException("Not an extensions file type");
    }

    public String getPath() {
        if (fileType.equals(S3FileType.Global))
            return String.format(filePathFormat.get(fileType), this.path);
        else
            return String.format(filePathFormat.get(fileType), this.organisationMediaDirectory, this.path);
    }
}
