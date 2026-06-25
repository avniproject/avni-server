package org.avni.server.service.storage;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.S3Service;

import java.util.function.Supplier;

/**
 * Request-scoped seam that exposes the per-org, per-data-class storage resolution to call sites
 * (avniproject/avni-server#1012). Story 4 (media serve/upload routing) injects this and calls
 * {@link #forDataClass(StorageDataClass)} to get the backend for {@code (currentOrg, dataClass)}.
 * <p>
 * The {@code DEFAULT} backend supplier captures today's selection (dev/staging minio branch or the
 * prod batch service) so an unconfigured org / DEFAULT data is byte-for-byte unchanged (D16).
 */
public class StorageServiceProvider {
    private final StorageResolver storageResolver;
    private final Supplier<S3Service> defaultProvider;

    public StorageServiceProvider(StorageResolver storageResolver, Supplier<S3Service> defaultProvider) {
        this.storageResolver = storageResolver;
        this.defaultProvider = defaultProvider;
    }

    public S3Service forDataClass(StorageDataClass dataClass) {
        Organisation organisation = UserContextHolder.getOrganisation();
        return storageResolver.resolve(organisation, dataClass, defaultProvider);
    }

    /** The default (today's) backend - equivalent to {@code forDataClass(DEFAULT)}. */
    public S3Service getDefault() {
        return defaultProvider.get();
    }
}
