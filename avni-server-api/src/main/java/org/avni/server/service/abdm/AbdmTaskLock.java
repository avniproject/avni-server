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

// With more than one server, each ABDM task runs on one at a time: a run holds a Postgres advisory lock named for its
// task, and a server finding it held skips that run. A session lock, not a transaction's: a run makes many transactions
// and calls Eka between them. Its connection is the run's own, outside the pool, since a pooled session outlives close()
// and would carry a lock left on it back into the pool. Postgres drops a session's locks when it ends: when the run
// closes it, or when a crashed server's connections close.
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

    private static DataSource unpooled(DataSourceProperties database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(database.determineUrl(), database.determineUsername(),
                database.determinePassword());
        dataSource.setDriverClassName(database.determineDriverClassName());
        return dataSource;
    }

    // Runs the work holding the task's lock and returns true; returns false at once, without running it, when another
    // server holds it. Unavailable when the lock cannot be asked for at all.
    public boolean runAlone(String task, Runnable work) {
        Connection connection;
        try {
            connection = unpooled.getConnection();
        } catch (SQLException e) {
            throw new Unavailable(task, e);
        }
        try {
            boolean locked;
            try {
                connection.setAutoCommit(true);
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
