package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Tabs {
    @JsonProperty("id")
    private int id;

    @JsonProperty("name")
    private String name;

    public Tabs(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
