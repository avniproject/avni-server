package org.avni.server.builder;

import org.junit.Test;

import static org.junit.Assert.*;

public class BuilderExceptionTest {
    @Test
    public void hasBundleSpecificMessage() {
        try {
            throw new BuilderException("userMessage", "bundleSpecificMessage");
        } catch (BuilderException be) {
            assertEquals("userMessage (bundleSpecificMessage)", be.getMessage());
            assertEquals("userMessage", be.getUserMessage());
        }
    }

    @Test
    public void doesntHaveBundleSpecificMessage() {
        try {
            throw new BuilderException("userMessage");
        } catch (BuilderException be) {
            assertEquals("userMessage", be.getMessage());
            assertEquals("userMessage", be.getUserMessage());
        }
    }
}
