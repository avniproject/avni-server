package org.avni.server.service.customimpl;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class EncounterStatusTest {

    @Test
    public void parsesKnownValuesCaseInsensitive() {
        assertEquals(EncounterStatus.SCHEDULED, EncounterStatus.from("scheduled"));
        assertEquals(EncounterStatus.SCHEDULED, EncounterStatus.from("Scheduled"));
        assertEquals(EncounterStatus.SCHEDULED, EncounterStatus.from("SCHEDULED"));
        assertEquals(EncounterStatus.COMPLETED, EncounterStatus.from("completed"));
        assertEquals(EncounterStatus.COMPLETED, EncounterStatus.from("COMPLETED"));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from("all"));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from("ALL"));
    }

    @Test
    public void defaultsToAllOnNullOrBlank() {
        assertEquals(EncounterStatus.ALL, EncounterStatus.from(null));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from(""));
        assertEquals(EncounterStatus.ALL, EncounterStatus.from("   "));
    }

    @Test
    public void rejectsUnknownValueAsBadRequest() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> EncounterStatus.from("nonsense"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("status must be one of scheduled, completed, all", ex.getReason());
    }
}
