package org.avni.server.framework.hibernate;

import org.avni.server.domain.ChecklistItemStatus;

public class ChecklistItemUserType extends AbstractJsonbUserType<ChecklistItemStatus> {
    @Override
    public Class returnedClass() {
        return ChecklistItemStatus.class;
    }
}
