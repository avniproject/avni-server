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

    @JsonProperty("json-unfolding")
    private boolean jsonUnfolding;

    @JsonProperty("advanced-options")
    private boolean advancedOptions;

    // org schema name
//    @JsonProperty("schema-filters-patterns")
//    private String schemaFilterPatterns;

//    @JsonProperty("schema-filters-type")
//    private String schemaFilterType = "inclusion";

    @JsonProperty("use-auth-provider")
    private boolean useAuthProvider;

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
        this.jsonUnfolding = false;
        this.advancedOptions = true;
        this.useAuthProvider = false;
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

    public boolean isJsonUnfolding() {
        return jsonUnfolding;
    }

    public boolean isAdvancedOptions() {
        return advancedOptions;
    }

    public boolean isUseAuthProvider() {
        return useAuthProvider;
    }
}
