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
    public void aLockWhoseConnectionIsLostDuringTheRunIsNotLeftHeld() {
        AtomicBoolean finished = new AtomicBoolean();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertTrue(taskLock.runAlone(TASK, () -> {
            jdbc.queryForObject("select pg_terminate_backend(?)", Boolean.class, holderSession(TASK));
            finished.set(true);
        }));

        assertTrue(finished.get());
        assertEquals(0, holders(TASK));
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
