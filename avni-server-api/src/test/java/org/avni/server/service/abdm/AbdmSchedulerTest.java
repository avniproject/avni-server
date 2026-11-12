package org.avni.server.service.abdm;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// ABDM's jobs and webhook worker run on ABDM's own threads, never on Spring's one scheduler thread that every other job
// shares.
public class AbdmSchedulerTest {
    private final AbdmScheduler scheduler = new AbdmScheduler();

    @After
    public void shutdown() {
        scheduler.shutdown();
    }

    @Test
    public void aScheduledTaskRunsOnAnAbdmThread() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();

        scheduler.schedule("* * * * * *", () -> {
            thread.set(Thread.currentThread().getName());
            ran.countDown();
        });

        assertTrue(ran.await(3, TimeUnit.SECONDS));
        assertTrue(thread.get(), thread.get().startsWith(AbdmScheduler.THREAD_PREFIX));
    }

    @Test
    public void workHandedOverRunsOnAnAbdmThreadAtOnce() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();

        scheduler.execute(() -> {
            thread.set(Thread.currentThread().getName());
            ran.countDown();
        });

        assertTrue(ran.await(3, TimeUnit.SECONDS));
        assertTrue(thread.get(), thread.get().startsWith(AbdmScheduler.THREAD_PREFIX));
    }

    @Test
    public void aDisabledCronSchedulesNothing() {
        scheduler.schedule("-", () -> {
            throw new AssertionError("never runs");
        });

        assertEquals(0, scheduler.scheduledCount());
    }

    @Test
    public void aTaskThatThrowsIsRunAgainAtItsNextTime() throws InterruptedException {
        CountDownLatch twice = new CountDownLatch(2);

        scheduler.schedule("* * * * * *", () -> {
            twice.countDown();
            throw new IllegalStateException("fails every time");
        });

        assertTrue(twice.await(4, TimeUnit.SECONDS));
    }
}
