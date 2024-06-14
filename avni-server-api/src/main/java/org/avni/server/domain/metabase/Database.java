package org.avni.server.domain.metabase;

public class Database {
    private final Integer id;
    private String name;
    private String engine;
    private DatabaseDetails details;

    public Database(String name, String engine, DatabaseDetails details) {
        this(null, name, engine, details);
    }

    public Database(Integer id, String name, String engine, DatabaseDetails details) {
        this.id = id;
        this.name = name;
        this.engine = engine;
        this.details = details;
    }

    public Integer getId() {
        return id;
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
