package org.avni.server.service;

import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.SyncableRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScopeBasedSyncService<T extends CHSEntity> {
    private final AddressLevelService addressLevelService;

    public ScopeBasedSyncService(AddressLevelService addressLevelService) {
        this.addressLevelService = addressLevelService;
    }

    public Page<T> getSyncResultsBySubjectTypeRegistrationLocation(SyncableRepository<T> repository, User user, DateTime lastModifiedDateTime, DateTime now, Long typeId, Pageable pageable, SubjectType subjectType, SyncEntityName syncEntityName) {
        List<Long> addressLevels = addressLevelService.getAllRegistrationAddressIdsBySubjectType(user.getCatchment(), subjectType);
        return repository.getSyncResults(new SyncParameters(lastModifiedDateTime, now, typeId, null, pageable, addressLevels, subjectType, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    public Page<T> getSyncResultsBySubjectTypeRegistrationLocation(SyncableRepository<T> repository, User user, DateTime lastModifiedDateTime, DateTime now, String entityTypeUuid, Pageable pageable, SubjectType subjectType, SyncEntityName syncEntityName) {
        List<Long> addressLevels = addressLevelService.getAllRegistrationAddressIdsBySubjectType(user.getCatchment(), subjectType);
        return repository.getSyncResults(new SyncParameters(lastModifiedDateTime, now, null, entityTypeUuid, pageable, addressLevels, subjectType, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    public Page<T> getSyncResultsByCatchment(SyncableRepository<T> repository, User user, DateTime lastModifiedDateTime, DateTime now, Pageable pageable, SyncEntityName syncEntityName) {
        return repository.getSyncResults(new SyncParameters(lastModifiedDateTime, now, null, null, pageable, null, null, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    public Slice<T> getSyncResultsBySubjectTypeRegistrationLocationAsSlice(SyncableRepository<T> repository, User user, DateTime lastModifiedDateTime, DateTime now, Long typeId, Pageable pageable, SubjectType subjectType, SyncEntityName syncEntityName) {
        List<Long> addressLevels = addressLevelService.getAllRegistrationAddressIdsBySubjectType(user.getCatchment(), subjectType);
        return repository.getSyncResultsAsSlice(new SyncParameters(lastModifiedDateTime, now, typeId, null, pageable, addressLevels, subjectType, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    public Slice<T> getSyncResultsBySubjectTypeRegistrationLocationAsSlice(SyncableRepository<T> repository, User user, DateTime lastModifiedDateTime, DateTime now, String entityTypeUuid, Pageable pageable, SubjectType subjectType, SyncEntityName syncEntityName) {
        List<Long> addressLevels = addressLevelService.getAllRegistrationAddressIdsBySubjectType(user.getCatchment(), subjectType);
        return repository.getSyncResultsAsSlice(new SyncParameters(lastModifiedDateTime, now, null, entityTypeUuid, pageable, addressLevels, subjectType, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    public Slice<T> getSyncResultsByCatchmentAsSlice(SyncableRepository<T> repository, User user, DateTime lastModifiedDateTime, DateTime now, Pageable pageable, SyncEntityName syncEntityName) {
        return repository.getSyncResultsAsSlice(new SyncParameters(lastModifiedDateTime, now, null, null, pageable, null, null, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }
}
