package org.avni.server.web;

import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.web.response.UserPrivilegeWebResponse;
import org.avni.server.web.response.UserInfoWebResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class UserInfoWebController {
    private final GroupPrivilegeService groupPrivilegeService;
    private final UserRepository userRepository;
    private final PrivilegeRepository privilegeRepository;

    @Autowired
    public UserInfoWebController(GroupPrivilegeService groupPrivilegeService, UserRepository userRepository, PrivilegeRepository privilegeRepository) {
        this.groupPrivilegeService = groupPrivilegeService;
        this.userRepository = userRepository;
        this.privilegeRepository = privilegeRepository;
    }

    @RequestMapping(value = "/web/userInfo", method = RequestMethod.GET)
    public UserInfoWebResponse getUserInfo() {
        Organisation contextOrganisation = UserContextHolder.getOrganisation();
        User contextUser = UserContextHolder.getUser();
        User user = userRepository.findOne(contextUser.getId());
        if (UserContextHolder.getUser().isAdmin()) {
            List<Privilege> adminPrivileges = privilegeRepository.getAdminPrivileges();
            List<UserPrivilegeWebResponse> groupPrivilegeResponses = adminPrivileges.stream()
                    .map(UserPrivilegeWebResponse::createForAdminUser).collect(Collectors.toList());
            return UserInfoWebResponse.createForAdminUser(groupPrivilegeResponses, contextOrganisation, user);
        }

        List<GroupPrivilege> groupPrivileges = groupPrivilegeService.getGroupPrivileges(user).getPrivileges();
        String usernameSuffix = contextOrganisation.getEffectiveUsernameSuffix();
        String catchmentName = user.getCatchment() == null ? null : user.getCatchment().getName();

        List<UserPrivilegeWebResponse> groupPrivilegeResponses = groupPrivileges.stream()
                .map(UserPrivilegeWebResponse::createForOrgUser)
                .distinct()
                .collect(Collectors.toList());
        return new UserInfoWebResponse(user.getUsername(),
                contextOrganisation.getName(),
                contextOrganisation.getId(),
                usernameSuffix,
                user.getSettings(),
                user.getName(),
                catchmentName,
                user.getSyncSettings(),
                groupPrivilegeResponses,
                user.hasAllPrivileges());
    }

}
