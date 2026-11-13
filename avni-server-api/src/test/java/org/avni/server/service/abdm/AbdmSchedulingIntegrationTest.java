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

    @Autowired
    private org.springframework.context.ApplicationContext context;

    // Carried from R2-3's review: the scheduler stops before the database pool, the entity manager factory and the
    // transaction manager go, so stopped work can still note its outcome. Each name it depends on is a bean of this app;
    // a wrong name would stop the server starting. The test context replaces the scheduler itself, so this reads the class.
    @Test
    public void theSchedulerDependsOnTheDatabaseBeansByTheirNamesInThisApp() {
        org.springframework.context.annotation.DependsOn dependsOn = AbdmScheduler.class.getAnnotation(org.springframework.context.annotation.DependsOn.class);

        assertTrue(java.util.Arrays.asList(dependsOn.value()).containsAll(List.of("dataSource", "entityManagerFactory", "transactionManager")));
        for (String bean : dependsOn.value()) assertTrue(bean, context.containsBean(bean));
    }
}
