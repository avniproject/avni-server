package org.avni.server.importer.batch.metabase;

import org.avni.server.domain.Organisation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class CannedAnalyticsLockProvider {
    private static final Map<String, ReentrantLock> organisationLocks = new HashMap<>();

    public static void acquireLock(Organisation organisation) {
        ReentrantLock organisationLock = organisationLocks.get(organisation.getUuid());
        if (organisationLock == null) {
            organisationLock = new ReentrantLock();
            organisationLocks.put(organisation.getUuid(), organisationLock);
        }
        organisationLock.lock();
    }

    public static void releaseLock(Organisation organisation) {
        ReentrantLock organisationLock = organisationLocks.get(organisation.getUuid());
        if (organisationLock == null) {
            throw new IllegalStateException("Lock not acquired for organisation: " + organisation.getUuid());
        }
        organisationLock.unlock();
    }
}
