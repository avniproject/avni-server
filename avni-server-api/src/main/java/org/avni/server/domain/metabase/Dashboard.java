package org.avni.server.domain.metabase;

import org.springframework.stereotype.Component;

@Component
public class Dashboard {
    private int id;
    private String name;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Dashboard{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
