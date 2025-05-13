package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDetails {
    public FieldDetails() {
    }

    public FieldDetails(String name) {
        this.name = name;
    }

    @JsonProperty("id")
    private int id;

    public void setId(int id) {
        this.id = id;
    }

    @JsonProperty("name")
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("base_type")
    private String baseType;

    @JsonProperty("semantic_type")
    private String semanticType;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseType() {
        return baseType;
    }

    public String getSemanticType() {
        return semanticType;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public static String toString(List<FieldDetails> fieldDetails) {
        return fieldDetails.stream()
                .map(FieldDetails::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
