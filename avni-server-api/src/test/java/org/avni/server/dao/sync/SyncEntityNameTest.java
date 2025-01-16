package org.avni.server.dao.sync;

import org.avni.server.domain.sync.SyncEntityName;
import org.junit.Test;

import static org.junit.Assert.*;

public class SyncEntityNameTest {
    @Test
    public void existsAsEnum() {
        assertFalse(SyncEntityName.existsAsEnum("SyncTelemetry"));
        assertFalse(SyncEntityName.existsAsEnum("VideoTelemetric"));
        assertTrue(SyncEntityName.existsAsEnum("IdentifierAssignment"));
    }
}
