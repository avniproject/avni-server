package org.avni.server.service.accessControl;

import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {
    private final UserRepository userRepository;

    @Autowired
    public AccessControlService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void checkPrivilege(PrivilegeType privilegeType) {
        this.checkPrivilege(UserContextHolder.getUser(), privilegeType);
    }

    public void checkPrivilege(User contextUser, PrivilegeType privilegeType) {
        if (contextUser == null) throw AvniAccessException.createNoUserException();
        if (!(userRepository.hasPrivilege(privilegeType.name(), contextUser.getId()) || userRepository.hasAllPrivileges(contextUser.getId()))) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType);
        }
    }
}
