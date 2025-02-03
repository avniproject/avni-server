package org.avni.server.dao;

import org.avni.server.domain.AddressLevel;

public interface SubjectTreeItemRepository {
    void voidSubjectItemsAt(AddressLevel address);
}
