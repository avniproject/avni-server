package org.avni.server.service.abdm;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Spring's default scheduler has one thread, shared by every organisation's messaging and storage jobs; no ABDM work is
// put on it.
public class AbdmSchedulingIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private List<ScheduledTaskHolder> scheduledTaskHolders;

    @Test
    public void noAbdmTaskIsOnSpringsDefaultScheduler() {
        boolean anyTask = false;
        for (ScheduledTaskHolder holder : scheduledTaskHolders) {
            for (ScheduledTask task : holder.getScheduledTasks()) {
                anyTask = true;
                String runnable = task.getTask().getRunnable().toString();
                assertFalse(runnable, runnable.contains(".abdm."));
            }
        }
        assertTrue("the default scheduler's tasks were not found", anyTask);
    }
}
