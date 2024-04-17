package org.avni.server.service;

import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.SyncableRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.framework.ApplicationContextProvider;
import org.joda.time.DateTime;

import java.util.List;

public interface ScopeAwareService<T extends CHSEntity> {

    default boolean isChangedBySubjectTypeRegistrationLocationType(User user, DateTime lastModifiedDateTime, Long typeId, SubjectType subjectType, SyncEntityName syncEntityName) {
        AddressLevelService addressLevelService = ApplicationContextProvider.getContext().getBean(AddressLevelService.class);
        List<Long> addressIds = addressLevelService.getAllRegistrationAddressIdsBySubjectType(user.getCatchment(), subjectType);
        return repository().isEntityChanged(new SyncParameters(lastModifiedDateTime, DateTime.now(), typeId, null, null, addressIds, subjectType, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    default boolean isChangedByCatchment(User user, DateTime lastModifiedDateTime, SyncEntityName syncEntityName) {
        return repository().isEntityChanged(new SyncParameters(lastModifiedDateTime, DateTime.now(), null, null, null, null, null, user.getSyncSettings(), syncEntityName, user.getCatchment()));
    }

    SyncableRepository<T> repository();

    boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String typeUUID);
}
