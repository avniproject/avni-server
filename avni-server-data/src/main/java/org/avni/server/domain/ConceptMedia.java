package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

public class ConceptMedia implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String CONCEPT_MEDIA_EXPORT_FILENAME_SEPARATOR = "--";
    private String url;
    private MediaType type;

    public ConceptMedia() {
    }

    public ConceptMedia(String url, MediaType type) {
        this.url = url;
        this.type = type;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("type")
    public MediaType getType() {
        return type;
    }

    public void setType(MediaType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConceptMedia that = (ConceptMedia) o;
        return Objects.equals(url, that.url) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, type);
    }

    public enum MediaType {
        Image, Video
    }

    public static String buildBundleFileName(String conceptUuid, MediaType type, int index, String s3FileName) {
        String sep = CONCEPT_MEDIA_EXPORT_FILENAME_SEPARATOR;
        return conceptUuid + sep + type + sep + String.format("%03d", index) + sep + s3FileName;
    }

    public static BundleFileNameParts parseBundleFileName(String fileName) {
        String sep = CONCEPT_MEDIA_EXPORT_FILENAME_SEPARATOR;
        String[] parts = fileName.split(sep, 4);
        String conceptUuid = parts[0];
        MediaType type = MediaType.valueOf(parts[1]);
        // New format has a 3-digit numeric index in parts[2]; legacy 3-part format does not.
        if (parts.length == 4 && parts[2].matches("\\d{3}")) {
            return new BundleFileNameParts(conceptUuid, type, Integer.parseInt(parts[2]), parts[3]);
        }
        String legacyFileName = fileName.substring(
                (conceptUuid + sep + parts[1] + sep).length());
        return new BundleFileNameParts(conceptUuid, type, null, legacyFileName);
    }

    public static class BundleFileNameParts {
        public final String conceptUuid;
        public final MediaType type;
        public final Integer index;
        public final String s3FileName;

        public BundleFileNameParts(String conceptUuid, MediaType type, Integer index, String s3FileName) {
            this.conceptUuid = conceptUuid;
            this.type = type;
            this.index = index;
            this.s3FileName = s3FileName;
        }
    }

}
