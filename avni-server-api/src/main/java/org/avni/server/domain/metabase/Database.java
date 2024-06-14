package org.avni.server.domain.metabase;

public class Database {
    private String name;
    private String engine;
    private DatabaseDetails details;

    public Database(String name, String engine, DatabaseDetails details) {
        this.name = name;
        this.engine = engine;
        this.details = details;
    }

    public String getName() {
        return name;
    }

    public String getEngine() {
        return engine;
    }

    public DatabaseDetails getDetails() {
        return details;
    }
}
