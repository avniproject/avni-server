package org.avni.server.domain.metabase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AvniDatabase {
    @Value("${avni.read.database.server}")
    private String avniReadDatabaseServer;

    @Value("${avni.database.port}")
    private String avniDatabasePort;

    @Value("${avni.database}")
    private String avniDatabaseName;

    @Value("${avni.database.server.supportsSSL}")
    private boolean supportsSSL;

    public AvniDatabase() {
    }

    public String getAvniReadDatabaseServer() {
        return avniReadDatabaseServer;
    }

    public String getAvniDatabasePort() {
        return avniDatabasePort;
    }

    public String getAvniDatabaseName() {
        return avniDatabaseName;
    }

    public boolean supportsSSL() {
        return supportsSSL;
    }
}
