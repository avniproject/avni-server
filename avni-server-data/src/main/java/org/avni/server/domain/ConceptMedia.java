package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class ConceptMedia implements Serializable {
    private static final long serialVersionUID = 1L;
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

    public enum MediaType {
        Image, Video
    }
}
