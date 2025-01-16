package org.avni.server.domain.metabase;

public class CreateCollectionRequest {
    private String name;
    private String description;

    public CreateCollectionRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
