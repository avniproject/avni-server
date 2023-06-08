package org.avni.server.web.request;

import org.avni.server.domain.ETLEntity;

public class ETLContract extends CHSRequest {
    private String name;
    private String dbUser;
    private String schemaName;

    public static void mapEntity(ETLContract contract, ETLEntity etlEntity) {
        contract.setName(etlEntity.getName());
        contract.setDbUser(etlEntity.getDbUser());
        contract.setSchemaName(etlEntity.getSchemaName());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }
}
