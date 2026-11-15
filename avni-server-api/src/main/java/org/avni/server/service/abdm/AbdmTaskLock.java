package org.avni.server.service.abdm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

// With more than one server, each ABDM task runs on one at a time: a run holds a Postgres advisory lock named for its
// task, and a server finding it held skips that run. It stops runs overlapping, not a second run in the same slot once
// the first has finished; the tasks are safe to run again. A session lock, not a transaction's: a run makes many
// transactions and calls Eka between them. Its connection is the run's own, outside the pool, since a pooled session
// outlives close() and would carry a lock left on it back into the pool; it must reach Postgres directly or through a
// pooler in session mode, as one in transaction mode would hand the session to others. Postgres drops a session's locks
// when it ends: when the run closes it, when a crashed server's connections close, or when keepalives find it gone.
@Component
public class AbdmTaskLock {
    private static final Logger logger = LoggerFactory.getLogger(AbdmTaskLock.class);
    // First of the two lock keys of every ABDM task; the second is the task's name hashed. Postgres keeps two-key advisory
    // locks apart from one-key ones, such as AbhaAdminService's.
    static final int ABDM_TASKS = 1087_000_002;

    private final DataSource unpooled;

    @Autowired
    public AbdmTaskLock(DataSourceProperties database) {
        this(unpooled(database));
    }

    AbdmTaskLock(DataSource unpooled) {
        this.unpooled = unpooled;
    }

    // A database that takes the connection but never answers is given up on, not waited on by an ABDM thread for ever.
    // Parameters in the URL win over these.
    static DriverManagerDataSource unpooled(DataSourceProperties database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(database.determineUrl(), database.determineUsername(),
                database.determinePassword());
        dataSource.setDriverClassName(database.determineDriverClassName());
        Properties properties = new Properties();
        properties.setProperty("loginTimeout", "10");
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("socketTimeout", "30");
        properties.setProperty("tcpKeepAlive", "true");
        dataSource.setConnectionProperties(properties);
        return dataSource;
    }

    // Runs the work holding the task's lock and returns true; returns false at once, without running it, when another
    // server holds it. Unavailable when the lock cannot be asked for at all.
    public boolean runAlone(String task, Runnable work) {
        Connection connection = open(task);
        try {
            boolean locked;
            try {
                locked = ask(connection, "select pg_try_advisory_lock(?, ?)", task);
            } catch (SQLException e) {
                throw new Unavailable(task, e);
            }
            if (!locked) return false;
            try {
                work.run();
                return true;
            } finally {
                release(connection, task);
            }
        } finally {
            close(connection, task);
        }
    }

    // The run's own connection, outside any transaction, opened fresh for each run.
    Connection open(String task) {
        Connection connection;
        try {
            connection = unpooled.getConnection();
        } catch (SQLException e) {
            throw new Unavailable(task, e);
        }
        try {
            connection.setAutoCommit(true);
            // Postgres probes a silent server after 60 s and ends its session, and the lock, about 30 s later.
            try (Statement statement = connection.createStatement()) {
                statement.execute("set tcp_keepalives_idle = 60");
                statement.execute("set tcp_keepalives_interval = 10");
                statement.execute("set tcp_keepalives_count = 3");
            }
            return connection;
        } catch (SQLException e) {
            close(connection, task);
            throw new Unavailable(task, e);
        }
    }

    // String.hashCode is fixed by the Java language, so every server derives the same key from the same task name.
    static int keyOf(String task) {
        return task.hashCode();
    }

    // Released at once, so the next run anywhere finds it free; closing the connection releases it anyway.
    private static void release(Connection connection, String task) {
        try {
            if (!ask(connection, "select pg_advisory_unlock(?, ?)", task)) {
                logger.warn("ABDM {}: its lock was no longer held when the run ended", task);
            }
        } catch (SQLException | RuntimeException e) {
            logger.warn("ABDM {}: its lock could not be released; closing its connection releases it", task, e);
        }
    }

    private static void close(Connection connection, String task) {
        try {
            connection.close();
        } catch (SQLException | RuntimeException e) {
            logger.warn("ABDM {}: the connection of its lock could not be closed cleanly", task, e);
        }
    }

    private static boolean ask(Connection connection, String sql, String task) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ABDM_TASKS);
            statement.setInt(2, keyOf(task));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    public static class Unavailable extends RuntimeException {
        public Unavailable(String task, Throwable cause) {
            super("The lock of ABDM " + task + " could not be asked for", cause);
        }
    }
}
