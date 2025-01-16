package org.avni.server.domain.metabase;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseQuery {
    private final int databaseId;
    private final ObjectNode queryNode;

    public MetabaseQuery(int databaseId, ObjectNode queryNode) {
        this.databaseId = databaseId;
        this.queryNode = queryNode;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public ObjectNode toJson() {
        return queryNode;
    }
}
