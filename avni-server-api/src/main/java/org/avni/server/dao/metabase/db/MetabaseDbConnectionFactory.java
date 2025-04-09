package org.avni.server.dao.metabase.db;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class MetabaseDbConnectionFactory {
    @Value("${metabase.db.url}")
    private String metabaseDbUrl;
    @Value("${metabase.db.user}")
    private String metabaseDbUser;
    @Value("${metabase.db.password}")
    private String metabaseDbPassword;

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(metabaseDbUrl, metabaseDbUser, metabaseDbPassword);
    }
}
