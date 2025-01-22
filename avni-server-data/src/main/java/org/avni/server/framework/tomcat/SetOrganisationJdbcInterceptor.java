package org.avni.server.framework.tomcat;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SetOrganisationJdbcInterceptor extends JdbcInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(SetOrganisationJdbcInterceptor.class);

    @Override
    public void reset(ConnectionPool connectionPool, PooledConnection pooledConnection) {
        UserContext userContext = UserContextHolder.getUserContext();
        if (userContext == null) {
            return;
        }
        Organisation organisation = userContext.getOrganisation();
        if (userContext.getUser() != null && userContext.getUser().isAdmin() && userContext.getOrganisationUUID() == null) {
            return;
        }
        if (organisation == null) return;

        String dbUser = organisation.getDbUser();
        if (ObjectUtils.isEmpty(dbUser)) return;

        try {
            Statement statement = pooledConnection.getConnection().createStatement();
            statement.execute("set role \"" + dbUser + "\";");
            statement.execute("set application_name to \"" + dbUser + "\";");
            statement.close();
            logger.trace(String.format("DB USER: %s", dbUser));
        } catch (SQLException exp) {
            throw new RuntimeException(exp);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("close".equals(method.getName())) {
            Statement statement = ((Connection) proxy).createStatement();
            statement.execute("RESET ROLE");
            statement.close();
            logger.trace("Role Reset Done");
        }
        return super.invoke(proxy, method, args);
    }
}
