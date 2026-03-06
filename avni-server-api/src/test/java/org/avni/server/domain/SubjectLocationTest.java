package org.avni.server.domain;

import org.avni.server.geo.Point;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

public class SubjectLocationTest {

    @Test
    public void equalsSameValues() {
        SubjectLocation a = new SubjectLocation(new Point(18.5, 73.8), 10.5);
        SubjectLocation b = new SubjectLocation(new Point(18.5, 73.8), 10.5);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalsItself() {
        SubjectLocation a = new SubjectLocation(new Point(18.5, 73.8), 10.5);
        assertEquals(a, a);
    }

    @Test
    public void notEqualToNull() {
        SubjectLocation a = new SubjectLocation(new Point(18.5, 73.8), 10.5);
        assertNotEquals(a, null);
    }

    @Test
    public void notEqualDifferentCoordinates() {
        SubjectLocation a = new SubjectLocation(new Point(18.5, 73.8), 10.5);
        SubjectLocation b = new SubjectLocation(new Point(19.0, 73.8), 10.5);
        assertNotEquals(a, b);
    }

    @Test
    public void notEqualDifferentAccuracy() {
        SubjectLocation a = new SubjectLocation(new Point(18.5, 73.8), 10.5);
        SubjectLocation b = new SubjectLocation(new Point(18.5, 73.8), 20.0);
        assertNotEquals(a, b);
    }

    @Test
    public void nullCoordinatesAndAccuracy() {
        SubjectLocation a = new SubjectLocation(null, null);
        SubjectLocation b = new SubjectLocation(null, null);
        assertEquals(a, b);
    }

    @Test
    public void equalsAfterJavaSerializationRoundtrip() throws Exception {
        SubjectLocation original = new SubjectLocation(new Point(18.5, 73.8), 10.5);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(original);
        oos.close();

        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        SubjectLocation deserialized = (SubjectLocation) new ObjectInputStream(bis).readObject();

        assertNotSame(original, deserialized);
        assertEquals(original, deserialized);
        assertEquals(original.hashCode(), deserialized.hashCode());
    }
}
