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

    public DatabaseDetails(AvniDatabase avniDatabase, String dbUser, String dbUserPassword) {
        this.host = avniDatabase.getAvniDatabaseServer();
        this.port = Integer.parseInt(avniDatabase.getAvniDatabasePort());
        this.dbname = avniDatabase.getAvniDatabaseName();
        this.user = dbUser;
        this.password = dbUserPassword;
        this.ssl = avniDatabase.supportsSSL();
        this.sslMode = "require";
        this.sslUseClientAuth = false;
        this.tunnelEnabled = false;
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
