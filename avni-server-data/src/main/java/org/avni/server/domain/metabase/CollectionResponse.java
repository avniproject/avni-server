package org.avni.server.domain.metabase;

import org.springframework.stereotype.Component;

@Component
public class CollectionResponse {
    private int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
