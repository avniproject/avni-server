package org.avni.server.service.abdm;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

// DEMO-ONLY abdm-hackathon: never merged.
// Runs webhook work off the request thread, as the organisation (AbdmOrgContext). Never throws to the caller.
// Failures are logged by class and message only, never a body. Tests run the work inline with inline(orgContext), or
// mock this class.
@Component
public class AbdmAsync {
    private static final Logger logger = LoggerFactory.getLogger(AbdmAsync.class);
    private static final int THREADS = 4;

    private final AbdmOrgContext orgContext;
    private final ScheduledExecutorService executor;

    @Autowired
    public AbdmAsync(AbdmOrgContext orgContext) {
        this(orgContext, Executors.newScheduledThreadPool(THREADS, daemonThreads()));
    }

    AbdmAsync(AbdmOrgContext orgContext, ScheduledExecutorService executor) {
        this.orgContext = orgContext;
        this.executor = executor;
    }

    // Runs everything on the caller's thread, at once. For tests.
    public static AbdmAsync inline(AbdmOrgContext orgContext) {
        return new AbdmAsync(orgContext, null) {
            @Override
            public void submit(String what, Runnable work) {
                run(what, work);
            }
        };
    }

    public void submit(String what, Runnable work) {
        try {
            executor.execute(() -> run(what, work));
        } catch (RejectedExecutionException e) {
            logger.error("ABDM work {} not started: the executor is shut down", what);
        }
    }

    protected void run(String what, Runnable work) {
        try {
            orgContext.runAsOrg(work);
        } catch (Throwable t) {
            logger.error("ABDM work {} failed: {}: {}", what, t.getClass().getSimpleName(), t.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) executor.shutdownNow();
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger count = new AtomicInteger();
        return work -> {
            Thread thread = new Thread(work, "abdm-async-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
