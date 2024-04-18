package org.avni.server.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

public interface SyncableRepository<T> {
    Page<T> getSyncResults(SyncParameters syncParameters);
    boolean isEntityChanged(SyncParameters syncParameters);
    Slice<T> getSyncResultsAsSlice(SyncParameters syncParameters);
}
