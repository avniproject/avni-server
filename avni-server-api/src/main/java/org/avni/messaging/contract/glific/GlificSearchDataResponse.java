package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "search"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificSearchDataResponse {

    @JsonProperty("search")
    private List<Search> search = new ArrayList<Search>();

    /**
     * No args constructor for use in serialization
     *
     */
    public GlificSearchDataResponse() {
    }

    /**
     *
     * @param search
     */
    public GlificSearchDataResponse(List<Search> search) {
        super();
        this.search = search;
    }

    @JsonProperty("search")
    public List<Search> getSearch() {
        return search;
    }

    @JsonProperty("search")
    public void setSearch(List<Search> search) {
        this.search = search;
    }

}
