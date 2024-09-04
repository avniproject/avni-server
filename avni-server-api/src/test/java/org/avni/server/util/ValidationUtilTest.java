package org.avni.server.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class ValidationUtilTest {
    @Test
    public void checkChars() {
        assertTrue(ValidationUtil.containsDisallowedPattern(">", ValidationUtil.COMMON_INVALID_CHARS_PATTERN));
        assertTrue(ValidationUtil.containsDisallowedPattern("\"", ValidationUtil.COMMON_INVALID_CHARS_PATTERN));
    }
}
