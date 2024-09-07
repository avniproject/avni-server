package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Database {
    private Integer id;
    private String name;
    private String engine;
    private DatabaseDetails details;
    

    public Database() {
    }
    
    public Database(String name, String engine, DatabaseDetails details) {
        this(null,name,engine,details);
    }

    public Database(Integer id,String name, String engine, DatabaseDetails details) {
        this.id=id;
        this.name = name;
        this.engine = engine;
        this.details = details;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public DatabaseDetails getDetails() {
        return details;
    }

    public void setDetails(DatabaseDetails details) {
        this.details = details;
    }


}
