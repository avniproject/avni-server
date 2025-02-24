package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseDetails {
    private String host;
    private String port;
    private String dbname;
    private String user;
    private String password;

    public DatabaseDetails() {
    }

    public DatabaseDetails(String host, String port, String dbname, String user, String password) {
        this.host = host;
        this.port = port;
        this.dbname = dbname;
        this.user = user;
        this.password = password;
    }

    public DatabaseDetails(AvniDatabase avniDatabase, String dbUser, String dbUserPassword) {
        this(avniDatabase.getAvniDatabaseServer(), avniDatabase.getAvniDatabasePort(), avniDatabase.getAvniDatabaseName(), dbUser, dbUserPassword);
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public String getDbname() {
        return dbname;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
