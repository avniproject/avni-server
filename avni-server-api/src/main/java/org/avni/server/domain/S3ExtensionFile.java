package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.util.S3File;
import org.joda.time.DateTime;

public class S3ExtensionFile {
    private final S3File s3File;
    private final DateTime lastModifiedDateTime;

    public S3ExtensionFile(S3File s3File, DateTime lastModifiedDateTime) {
        this.s3File = s3File;
        this.lastModifiedDateTime = lastModifiedDateTime;
    }

    @JsonProperty("url")
    public String getExtensionFilePath() {
        return s3File.getFilePathRelativeToExtension();
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    @JsonIgnore
    public S3File getS3File() {
        return s3File;
    }
}
