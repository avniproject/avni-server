package org.avni.server.util;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BooleanUtilTest {
    @Test
    public void getBoolean() {
        assertTrue(BooleanUtil.getBoolean(null, true));
        assertFalse(BooleanUtil.getBoolean(null, false));

        assertTrue(BooleanUtil.getBoolean(true, true));
        assertTrue(BooleanUtil.getBoolean(true, false));

        assertFalse(BooleanUtil.getBoolean(false, true));
        assertFalse(BooleanUtil.getBoolean(false, false));
    }
}
