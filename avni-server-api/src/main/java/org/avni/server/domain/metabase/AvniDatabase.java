package org.avni.server.domain.metabase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AvniDatabase {
    @Value("${avni.database.server}")
    private String avniDatabaseServer;

    @Value("${avni.database.port}")
    private String avniDatabasePort;

    @Value("${avni.database}")
    private String avniDatabaseName;

    public AvniDatabase() {
    }

    public String getAvniDatabaseServer() {
        return avniDatabaseServer;
    }

    public String getAvniDatabasePort() {
        return avniDatabasePort;
    }

    public String getAvniDatabaseName() {
        return avniDatabaseName;
    }
}
