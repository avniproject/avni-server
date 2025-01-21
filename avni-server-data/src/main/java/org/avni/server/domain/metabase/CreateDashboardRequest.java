package org.avni.server.domain.metabase;

public class CreateDashboardRequest {
    private final String name;
    private final String description;
    private final Integer collection_id;

    public CreateDashboardRequest(String description, Integer collection_id) {
        this.name = DashboardName.CANNED_REPORTS.getName();
        this.description = description;
        this.collection_id = collection_id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getCollection_id() {
        return collection_id;
    }

    @Override
    public String toString() {
        return "{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", collectionId=" + collection_id +
                '}';
    }
}
