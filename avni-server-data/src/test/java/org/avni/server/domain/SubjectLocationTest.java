package org.avni.server.domain;

import org.avni.server.geo.Point;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SubjectLocationTest {
    @Test
    void readsAsCoordinatesRatherThanAnObjectReference() {
        SubjectLocation subjectLocation = new SubjectLocation(new Point(13.940623, 76.617102), 12.5);

        assertEquals(new Point(13.940623, 76.617102).toString(), subjectLocation.toString());
        assertFalse(subjectLocation.toString().contains("SubjectLocation@"));
    }

    @Test
    void readsAsNothingWhenNoCoordinatesWereRecorded() {
        assertEquals("", new SubjectLocation(null, null).toString());
        assertEquals("", new SubjectLocation().toString());
    }
}
