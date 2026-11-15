package org.avni.server.service.abdm;

import org.junit.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// The lock's own connection: what it does when the database cannot be reached, and how long it waits on one.
public class AbdmTaskLockTest {
    private static final String TASK = "taskLockUnitTest";
    private static final String NOTHING_LISTENS = "jdbc:postgresql://127.0.0.1:1/none";

    @Test
    public void aDataSourceThatCannotConnectMeansUnavailableAndNothingRuns() throws SQLException {
        DataSource unreachable = mock(DataSource.class);
        when(unreachable.getConnection()).thenThrow(new SQLException("connection refused"));
        AtomicBoolean ran = new AtomicBoolean();

        AbdmTaskLock.Unavailable unavailable = assertThrows(AbdmTaskLock.Unavailable.class,
                () -> new AbdmTaskLock(unreachable).runAlone(TASK, () -> ran.set(true)));

        assertFalse(ran.get());
        assertTrue(unavailable.getCause() instanceof SQLException);
    }

    @Test
    public void aDatabaseNothingListensForMeansUnavailableAndNothingRuns() {
        AtomicBoolean ran = new AtomicBoolean();

        assertThrows(AbdmTaskLock.Unavailable.class, () -> new AbdmTaskLock(database(NOTHING_LISTENS)).runAlone(TASK, () -> ran.set(true)));

        assertFalse(ran.get());
    }

    // A database that takes the connection but never answers is given up on, and the connection is kept alive from this
    // end; the URL given can still say otherwise.
    @Test
    public void theLockConnectionWaitsOnTheDatabaseOnlySoLong() {
        DriverManagerDataSource unpooled = AbdmTaskLock.unpooled(database(NOTHING_LISTENS));

        Properties properties = unpooled.getConnectionProperties();
        assertNotNull(properties);
        assertEquals("10", properties.getProperty("loginTimeout"));
        assertEquals("10", properties.getProperty("connectTimeout"));
        assertEquals("30", properties.getProperty("socketTimeout"));
        assertEquals("true", properties.getProperty("tcpKeepAlive"));
        assertEquals(NOTHING_LISTENS, unpooled.getUrl());
    }

    private static DataSourceProperties database(String url) {
        DataSourceProperties database = new DataSourceProperties();
        database.setUrl(url);
        database.setUsername("nobody");
        database.setPassword("not-a-password");
        database.setDriverClassName("org.postgresql.Driver");
        return database;
    }
}
