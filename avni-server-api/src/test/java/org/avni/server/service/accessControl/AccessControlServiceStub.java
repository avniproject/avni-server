package org.avni.server.service.accessControl;

import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;

public class AccessControlServiceStub extends AccessControlService {
    public AccessControlServiceStub() {
        super(null);
    }

    @Override
    public void checkPrivilege(PrivilegeType privilegeType) {
    }

    @Override
    public void checkPrivilege(User contextUser, PrivilegeType privilegeType) {
    }
}
