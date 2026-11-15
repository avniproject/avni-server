package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

// With more than one server, each ABDM housekeeping task runs on one at a time: a run holds a Postgres advisory lock named
// for its task, and a server that finds it held skips that run. Here another connection stands in for the other server.
public class AbdmTaskLockIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String TASK = "taskLockTest";
    private static final String OTHER_TASK = "taskLockTestOther";

    @Autowired
    private AbdmTaskLock taskLock;
    @Autowired
    private AbdmJobRunner runner;
    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;

    @Test
    public void withTheLockHeldByAnotherConnectionTheWorkDoesNotRun() throws SQLException {
        AtomicBoolean ran = new AtomicBoolean();
        try (Connection otherServer = dataSource.getConnection()) {
            assertTrue(lock(otherServer, TASK));
            try {
                assertFalse(taskLock.runAlone(TASK, () -> ran.set(true)));
            } finally {
                unlock(otherServer, TASK);
            }
        }
        assertFalse(ran.get());
    }

    @Test
    public void withTheLockFreeTheWorkRunsHoldingItAndItIsReleasedAfter() {
        long[] holdersDuringTheRun = new long[1];

        assertTrue(taskLock.runAlone(TASK, () -> holdersDuringTheRun[0] = holders(TASK)));

        assertEquals(1, holdersDuringTheRun[0]);
        assertEquals(0, holders(TASK));
    }

    @Test
    public void aRunThatFailsStillReleasesTheLock() {
        assertThrows(IllegalStateException.class, () -> taskLock.runAlone(TASK, () -> {
            throw new IllegalStateException("the run failed");
        }));

        assertEquals(0, holders(TASK));
    }

    @Test
    public void aTaskHeldElsewhereDoesNotStopAnotherTask() throws SQLException {
        AtomicBoolean ran = new AtomicBoolean();
        try (Connection otherServer = dataSource.getConnection()) {
            assertTrue(lock(otherServer, TASK));
            try {
                assertTrue(taskLock.runAlone(OTHER_TASK, () -> ran.set(true)));
            } finally {
                unlock(otherServer, TASK);
            }
        }
        assertTrue(ran.get());
    }

    @Test
    public void theHousekeepingTasksEachHaveALockOfTheirOwn() {
        Set<Integer> keys = Stream.of(AbdmHousekeepingJob.VISIT_LINKING, AbdmHousekeepingJob.CONSENT_EXPIRY,
                        AbdmHousekeepingJob.CONSENT_UPDATE_RETRY, AbdmHousekeepingJob.TRAFFIC_PURGE)
                .map(AbdmTaskLock::keyOf).collect(Collectors.toSet());

        assertEquals(4, keys.size());
    }

    // A pooled connection's session outlives close(), and a session lock with it. The lock's session is its own instead,
    // and ends with the run, so nothing of it is left in the pool.
    @Test
    public void theLockIsHeldOnASessionOfItsOwnThatEndsWithTheRun() throws Exception {
        int[] lockSession = new int[1];

        assertTrue(taskLock.runAlone(TASK, () -> lockSession[0] = holderSession(TASK)));

        awaitSessionGone(lockSession[0]);
    }

    // The lock's connection lost during the run (here, its session ended from outside): the run finishes, and the lock is
    // not left held, since a session's locks end with it.
    @Test
    public void aLockWhoseConnectionIsLostDuringTheRunIsNotLeftHeld() throws InterruptedException {
        AtomicBoolean finished = new AtomicBoolean();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertTrue(taskLock.runAlone(TASK, () -> {
            jdbc.queryForObject("select pg_terminate_backend(?)", Boolean.class, holderSession(TASK));
            finished.set(true);
        }));

        assertTrue(finished.get());
        awaitNoHolders(TASK);
    }

    // The lock's connection gives up on a database that stops answering, and Postgres drops the session of a server that
    // has vanished, and its lock, within about 90 seconds (60 idle, then 3 probes 10 apart).
    @Test
    public void theLockConnectionWaitsOnlySoLongAndIsKeptAlive() throws SQLException {
        try (Connection connection = taskLock.open(TASK)) {
            assertTrue(connection.getAutoCommit());
            assertEquals(30_000, connection.getNetworkTimeout());
            assertEquals("60", show(connection, "tcp_keepalives_idle"));
            assertEquals("10", show(connection, "tcp_keepalives_interval"));
            assertEquals("3", show(connection, "tcp_keepalives_count"));
        }
    }

    // The runner against Postgres: while another server runs the task, this one does not even read its organisations.
    @Test
    public void theRunnerSkipsARunWhileAnotherServerHoldsTheTask() throws SQLException {
        AtomicBoolean read = new AtomicBoolean();
        Supplier<List<Long>> selection = () -> {
            read.set(true);
            return List.of();
        };
        try (Connection otherServer = dataSource.getConnection()) {
            assertTrue(lock(otherServer, TASK));
            try {
                runner.forOrganisations(TASK, Duration.ofSeconds(30), selection, deadline -> fail("no organisation"));
            } finally {
                unlock(otherServer, TASK);
            }
        }
        assertFalse(read.get());

        runner.forOrganisations(TASK, Duration.ofSeconds(30), selection, deadline -> fail("no organisation"));

        assertTrue(read.get());
        assertEquals(0, holders(TASK));
    }

    private static boolean lock(Connection connection, String task) throws SQLException {
        return booleanOf(connection, "select pg_try_advisory_lock(?, ?)", task);
    }

    private static void unlock(Connection connection, String task) throws SQLException {
        assertTrue(booleanOf(connection, "select pg_advisory_unlock(?, ?)", task));
    }

    private static boolean booleanOf(Connection connection, String sql, String task) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, AbdmTaskLock.ABDM_TASKS);
            statement.setInt(2, AbdmTaskLock.keyOf(task));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    // Sessions holding the task's lock, from any connection: pg_locks shows the two keys as unsigned oids.
    private long holders(String task) {
        return new JdbcTemplate(dataSource).queryForObject("select count(*) from pg_locks where " + heldLock(), Long.class,
                Integer.toUnsignedLong(AbdmTaskLock.ABDM_TASKS), Integer.toUnsignedLong(AbdmTaskLock.keyOf(task)));
    }

    private int holderSession(String task) {
        return new JdbcTemplate(dataSource).queryForObject("select pid from pg_locks where " + heldLock(), Integer.class,
                Integer.toUnsignedLong(AbdmTaskLock.ABDM_TASKS), Integer.toUnsignedLong(AbdmTaskLock.keyOf(task)));
    }

    private static String show(Connection connection, String setting) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("show " + setting);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getString(1);
        }
    }

    // A session's locks go when Postgres has finished ending it, a moment after it is told to end.
    private void awaitNoHolders(String task) throws InterruptedException {
        long until = System.currentTimeMillis() + 5_000;
        long holders = holders(task);
        while (holders > 0 && System.currentTimeMillis() < until) {
            Thread.sleep(50);
            holders = holders(task);
        }
        assertEquals(0, holders);
    }

    private static String heldLock() {
        return "locktype = 'advisory' and classid::bigint = ? and objid::bigint = ? and objsubid = 2 and granted";
    }

    private void awaitSessionGone(int session) throws InterruptedException {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long until = System.currentTimeMillis() + 5_000;
        long alive = 1;
        while (alive > 0 && System.currentTimeMillis() < until) {
            alive = jdbc.queryForObject("select count(*) from pg_stat_activity where pid = ?", Long.class, session);
            if (alive > 0) Thread.sleep(50);
        }
        assertEquals("the session was returned to the pool, not ended", 0, alive);
    }
}
