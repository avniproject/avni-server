package org.avni.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncTimerTest {
    @Test
    public void timeLeft() throws InterruptedException {
        SyncTimer syncTimer = SyncTimer.fromMillis(1);
        syncTimer.start();
        Thread.sleep(2);
        long timeLeft = syncTimer.getTimeLeft();
        assertTrue(timeLeft < 0);
    }
}
