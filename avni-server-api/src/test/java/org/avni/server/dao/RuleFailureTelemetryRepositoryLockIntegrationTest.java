package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuleFailureTelemetryRepositoryLockIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private RuleFailureTelemetryRepository ruleFailureTelemetryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    public void onlyOneTransactionAtATimeGetsTheLockForTheSameFailure() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstHoldsTheLock = new CountDownLatch(1);
        CountDownLatch secondHasTried = new CountDownLatch(1);

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            boolean locked = ruleFailureTelemetryRepository.tryLockFailureUntilTransactionEnds("rule", "subject", "message");
            firstHoldsTheLock.countDown();
            await(secondHasTried);
            return locked;
        }));
        await(firstHoldsTheLock);
        boolean secondWhileFirstIsOpen = transaction.execute(status ->
                ruleFailureTelemetryRepository.tryLockFailureUntilTransactionEnds("rule", "subject", "message"));
        boolean otherFailureWhileFirstIsOpen = transaction.execute(status ->
                ruleFailureTelemetryRepository.tryLockFailureUntilTransactionEnds("rule", "subject", "other message"));
        secondHasTried.countDown();

        assertTrue(first.get(30, TimeUnit.SECONDS));
        assertFalse(secondWhileFirstIsOpen);
        assertTrue(otherFailureWhileFirstIsOpen);
        assertTrue(transaction.execute(status ->
                ruleFailureTelemetryRepository.tryLockFailureUntilTransactionEnds("rule", "subject", "message")));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for the other transaction");
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
