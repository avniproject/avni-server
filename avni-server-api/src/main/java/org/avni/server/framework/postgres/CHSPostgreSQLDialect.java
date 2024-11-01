package org.avni.server.framework.postgres;

import org.hibernate.dialect.PostgreSQL10Dialect;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import java.sql.Types;

public class CHSPostgreSQLDialect extends PostgreSQL10Dialect {
    private static final String JSONB = "jsonb";
    private static final String JSON = "json";

    public CHSPostgreSQLDialect() {
        super();
//        this.registerColumnTypes();
//        this.registerColumnType(Types.JAVA_OBJECT, JSONB);
//        this.registerColumnType(Types.JAVA_OBJECT, JSON);
    }
}
