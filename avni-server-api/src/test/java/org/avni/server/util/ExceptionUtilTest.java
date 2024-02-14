package org.avni.server.util;

import org.junit.Test;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class ExceptionUtilTest {
    @Test
    public void getFullStackTrace() {
        try {
            throw new RuntimeException(new NullPointerException("fooFoo"));
        } catch (Exception e) {
            String fullStackTrace = ExceptionUtil.getFullStackTrace(e);
            assertTrue(fullStackTrace.contains("fooFoo"));
            assertTrue(fullStackTrace.contains("NullPointerException"));
            assertTrue(fullStackTrace.contains("RuntimeException"));
        }
    }
}
