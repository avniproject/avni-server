package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableFieldsResponse {
    private List<FieldDetails> fields;

    public List<FieldDetails> getFields() {
        return fields;
    }

    public void setFields(List<FieldDetails> fields) {
        this.fields = fields;
    }
}
