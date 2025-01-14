package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Parameters {
    @JsonProperty("name")
    private final String name;
    @JsonProperty("slug")
    private final String slug;
    @JsonProperty("id")
    private final String id;
    @JsonProperty("type")
    private final String type;
    @JsonProperty("sectionId")
    private final String sectionId;

    public Parameters(String name, String slug, String id, String type, String sectionId) {
        this.name = name;
        this.slug = slug;
        this.id = id;
        this.type = type;
        this.sectionId = sectionId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getSectionId() {
        return sectionId;
    }
}
