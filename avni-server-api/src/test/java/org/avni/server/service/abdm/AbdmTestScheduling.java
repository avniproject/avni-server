package org.avni.server.service.abdm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

// In the shared test context ABDM's threads run nothing: a test drains the inbox itself (EkaEventWorker.drain), so no
// drain a webhook woke outlives its test and takes another test's rows. Replaces the AbdmScheduler bean by name.
@Configuration
public class AbdmTestScheduling {
    @Bean
    public AbdmScheduler abdmScheduler() {
        return new ManualScheduler();
    }

    public static class ManualScheduler extends AbdmScheduler {
        private final AtomicInteger executeRequests = new AtomicInteger();

        @Override
        public void schedule(String cron, Runnable task) {
        }

        @Override
        public void execute(Runnable task) {
            executeRequests.incrementAndGet();
        }

        public int executeRequests() {
            return executeRequests.get();
        }
    }
}
