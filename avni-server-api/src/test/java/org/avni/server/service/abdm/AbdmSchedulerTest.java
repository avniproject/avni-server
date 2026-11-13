package org.avni.server.service.abdm;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    // Carried from R2-1: stopping interrupts ABDM's work, never waits for it to finish, but gives it time to note its
    // outcome before the shutdown goes on and the database pool closes.
    @Test
    public void stoppingInterruptsTheWork_andWaitsForItToNoteItsOutcome() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean noted = new java.util.concurrent.atomic.AtomicBoolean();
        scheduler.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
                while (System.nanoTime() < until) Thread.onSpinWait();
                noted.set(true);
            }
        });
        assertTrue(started.await(3, TimeUnit.SECONDS));

        long before = System.nanoTime();
        scheduler.shutdown();

        assertTrue(interrupted.get());
        assertTrue(noted.get());
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - before) < 30);
    }

    // Carried from R2-3's review: work sees the scheduler stopping even after it, or the pool, cleared its interrupt flag.
    @Test
    public void workSeesTheSchedulerStopping_evenWithItsInterruptCleared() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean seenBefore = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicBoolean seenAfter = new java.util.concurrent.atomic.AtomicBoolean();
        scheduler.execute(() -> {
            seenBefore.set(scheduler.toldToStop());
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.interrupted();
                seenAfter.set(scheduler.toldToStop());
            }
        });
        assertTrue(started.await(3, TimeUnit.SECONDS));

        scheduler.shutdown();

        assertFalse(seenBefore.get());
        assertTrue(seenAfter.get());
        assertTrue(scheduler.toldToStop());
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
