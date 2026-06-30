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

}
