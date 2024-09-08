package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDetails {

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

    @JsonProperty("table_name")
    private String tableName;

    @JsonProperty("schema")
    private String schema;

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

    public String getTableName() {
        return tableName;
    }


    public String getSchema() {
        return schema;
    }

}
