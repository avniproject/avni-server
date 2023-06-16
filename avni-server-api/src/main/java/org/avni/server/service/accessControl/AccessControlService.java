package org.avni.server.service.accessControl;

import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {
    private final UserRepository userRepository;

    @Autowired
    public AccessControlService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean checkPrivilege(User contextUser, PrivilegeType editSubjectType) {
        User user = userRepository.getUser(contextUser.getUuid());
        user.getUserGroups();
        return true;
    }
}
