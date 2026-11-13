package org.avni.server.service.abdm;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

// ABDM's jobs and its webhook worker run on these threads of their own. Spring's default scheduler has one thread, which
// every organisation's messaging and storage jobs share, so a long ABDM run there would hold them all up.
// Deliberately not a Spring bean of type TaskScheduler: one would become the default scheduler for every @Scheduled job.
// Stopped before the database pool closes (it depends on it), so stopped work can still note what it did.
@Component
@DependsOn("dataSource")
public class AbdmScheduler {
    private static final Logger logger = LoggerFactory.getLogger(AbdmScheduler.class);
    public static final String THREAD_PREFIX = "abdm-";
    // Visit linking, consent upkeep, the webhook worker and one spare, so no one of them waits on another.
    private static final int THREADS = 4;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    // On stopping, work is interrupted, not waited for, and given this long to note its outcome.
    static final int STOP_GRACE_SECONDS = 30;

    private final ThreadPoolTaskScheduler scheduler;
    private final AtomicInteger scheduled = new AtomicInteger();

    public AbdmScheduler() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(THREADS);
        scheduler.setThreadNamePrefix(THREAD_PREFIX);
        scheduler.setErrorHandler(t -> logger.error("ABDM scheduled work failed", t));
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(STOP_GRACE_SECONDS);
        scheduler.initialize();
    }

    // A cron of "-" schedules nothing, as with @Scheduled.
    public void schedule(String cron, Runnable task) {
        if (cron == null || Scheduled.CRON_DISABLED.equals(cron.trim())) return;
        scheduler.schedule(task, new CronTrigger(cron.trim(), ZONE));
        scheduled.incrementAndGet();
    }

    // Runs the work once, as soon as a thread is free.
    public void execute(Runnable task) {
        try {
            scheduler.execute(task);
        } catch (TaskRejectedException e) {
            logger.warn("ABDM work not started: the ABDM threads are shut down");
        }
    }

    int scheduledCount() {
        return scheduled.get();
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
