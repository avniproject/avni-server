package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MetabaseAllUsersResponse {
    @JsonProperty("data")
    private List<MetabaseUserData> data;

    @JsonProperty("total")
    private int total;

    public List<MetabaseUserData> getData() {
        return data;
    }

    public int getTotal() {
        return total;
    }

}
