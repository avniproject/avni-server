package org.avni.server.service.storage;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.S3Service;

import java.util.function.Supplier;

// Exposes per-org, per-data-class storage resolution to call sites (resolves against the current org).
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

    public S3Service getDefault() {
        return defaultProvider.get();
    }
}
