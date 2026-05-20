package org.avni.server.service.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class EncounterStatusTest {

    @Test
    public void parsesKnownValuesCaseInsensitive() {
        assertEquals(EncounterStatus.SCHEDULED, EncounterStatus.from("scheduled"));
        assertEquals(EncounterStatus.SCHEDULED, EncounterStatus.from("Scheduled"));
        assertEquals(EncounterStatus.COMPLETED, EncounterStatus.from("completed"));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from("all"));
    }

    @Test
    public void defaultsToAllOnNullOrBlank() {
        assertEquals(EncounterStatus.ALL, EncounterStatus.from(null));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from(""));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from("   "));
    }

    @Test
    public void rejectsUnknownValue() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> EncounterStatus.from("nonsense"));
        assertEquals("status must be one of scheduled, completed, all", ex.getMessage());
    }
}
