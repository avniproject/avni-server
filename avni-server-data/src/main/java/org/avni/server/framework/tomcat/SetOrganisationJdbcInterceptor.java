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
import java.util.Arrays;

public class SetOrganisationJdbcInterceptor extends JdbcInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(SetOrganisationJdbcInterceptor.class);

    @Override
    public void reset(ConnectionPool connectionPool, PooledConnection pooledConnection) {
        int connectionId = pooledConnection.getConnection().hashCode();
        logger.trace(String.format("Getting connection out of connection pool. ConnectionId: %s", connectionId));
        UserContext userContext = UserContextHolder.getUserContext();
        if (userContext == null) {
            logger.trace(String.format("Getting connection out of connection pool. ConnectionId: %s. No user context present", connectionId));
            return;
        }
        Organisation organisation = userContext.getOrganisation();
        if (userContext.getUser() != null && userContext.getUser().isAdmin() && userContext.getOrganisationUUID() == null) {
            logger.trace(String.format("Getting connection out of connection pool. ConnectionId: %s. SuperAdmin user", connectionId));
            return;
        }
        if (organisation == null) {
            logger.trace(String.format("Getting connection out of connection pool. ConnectionId: %s. No organisation present", connectionId));
            return;
        }

        String dbUser = organisation.getDbUser();
        if (ObjectUtils.isEmpty(dbUser)) {
            logger.trace(String.format("Getting connection out of connection pool. ConnectionId: %s. No db user present", connectionId));
            return;
        }

        try {
            Statement statement = pooledConnection.getConnection().createStatement();
            statement.execute("set role \"" + dbUser + "\";");
            statement.execute("set application_name to \"" + dbUser + "\";");
            statement.close();
            logger.trace(String.format("Getting connection out of connection pool. ConnectionId: %s. Set db user: %s", connectionId, dbUser));
        } catch (SQLException exp) {
            throw new RuntimeException(exp);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Arrays.asList( "getMetaData", "toString").contains(method.getName()))
            return super.invoke(proxy, method, args);

        Connection connection = (Connection) proxy;
        logger.trace("Invoked operation {} on Connection: {}", method.getName(), connection.getMetaData().getConnection().hashCode());
        if ("close".equals(method.getName())) {
            Statement statement = connection.createStatement();
            statement.execute("RESET ROLE");
            statement.close();
            logger.trace("Role Reset Done");
        }
        return super.invoke(proxy, method, args);
    }
}
