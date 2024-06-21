package org.avni.server.domain.metabase;

import org.springframework.stereotype.Component;

@Component
public class DatabaseDetails {
    private String host;
    private String port;
    private String db;
    private String user;

    public DatabaseDetails() {
    }

    public DatabaseDetails(String host, String port, String db, String user) {
        this.host = host;
        this.port = port;
        this.db = db;
        this.user = user;
    }

    public DatabaseDetails(AvniDatabase avniDatabase, String dbUser) {
        this(avniDatabase.getAvniDatabaseServer(), avniDatabase.getAvniDatabasePort(), avniDatabase.getAvniDatabaseName(), dbUser);
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getDb() {
        return db;
    }

    public String getUser() {
        return user;
    }
}
