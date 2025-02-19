package org.avni.server.domain.metabase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DatabaseDetails {
    private String host;
    private int port;
    private String dbname;
    private String user;
    private String password;
    private boolean ssl;

    @JsonProperty("ssl-mode")
    private String sslMode;

    @JsonProperty("ssl-use-client-auth")
    private boolean sslUseClientAuth;

    @JsonProperty("tunnel-enabled")
    private boolean tunnelEnabled;

    public DatabaseDetails() {
    }

    public DatabaseDetails(String host, int port, String dbname, String user, String password) {
        this.host = host;
        this.port = port;
        this.dbname = dbname;
        this.user = user;
        this.password = password;
        this.ssl = true;
        this.sslMode = "require";
        this.sslUseClientAuth = false;
        this.tunnelEnabled = false;
    }

    public DatabaseDetails(AvniDatabase avniDatabase, String dbUser, String dbUserPassword) {
        this(avniDatabase.getAvniDatabaseServer(), Integer.parseInt(avniDatabase.getAvniDatabasePort()), avniDatabase.getAvniDatabaseName(), dbUser, dbUserPassword);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
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

    public boolean isSsl() {
        return ssl;
    }

    public String getSslMode() {
        return sslMode;
    }

    public boolean isSslUseClientAuth() {
        return sslUseClientAuth;
    }

    public boolean isTunnelEnabled() {
        return tunnelEnabled;
    }
}
