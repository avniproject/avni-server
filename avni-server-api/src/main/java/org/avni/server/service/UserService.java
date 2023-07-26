package org.avni.server.service;

import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.*;

import static org.avni.messaging.domain.Constants.NO_OF_DIGITS_IN_INDIAN_MOBILE_NO;

import org.avni.server.framework.security.UserContextHolder;
import org.bouncycastle.util.Strings;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements NonScopeAwareService {
    private static Logger logger = LoggerFactory.getLogger(UserService.class);
    private UserRepository userRepository;
    private OrganisationRepository organisationRepository;
    private GroupRepository groupRepository;
    private UserGroupRepository userGroupRepository;

    @Autowired
    public UserService(UserRepository userRepository, OrganisationRepository organisationRepository, GroupRepository groupRepository, UserGroupRepository userGroupRepository) {
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.groupRepository = groupRepository;
        this.userGroupRepository = userGroupRepository;
    }

    public User getCurrentUser() {
        UserContext userContext = UserContextHolder.getUserContext();
        return userContext.getUser();
    }

    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public void addToDefaultUserGroup(User user) {
        if (user.getOrganisationId() != null) {
            Group group = groupRepository.findByNameAndOrganisationId(Group.Everyone, user.getOrganisationId());
            UserGroup userGroup = UserGroup.createMembership(user, group);
            userGroupRepository.save(userGroup);
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return userRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    @Transactional
    public User findByUuid(String uuid) {
        return userRepository.findByUuid(uuid);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByPhoneNumber(String phoneNumber) {
        phoneNumber = phoneNumber.substring(phoneNumber.length() - NO_OF_DIGITS_IN_INDIAN_MOBILE_NO);
        return userRepository.findUserWithMatchingPropertyValue("phoneNumber", phoneNumber);
    }

    @Transactional
    public void addToGroups(User user, String groupsSpecified) {
        if (groupsSpecified == null) {
            this.addToDefaultUserGroup(user);
            return;
        }

        String[] groupNames = Strings.split(groupsSpecified, '|');
        Arrays.stream(groupNames).distinct().forEach(groupName -> {
            if (!StringUtils.hasLength(groupName.trim())) return;

            Group group = this.groupRepository.findByName(groupName);
            if (group == null) {
                String errorMessage = String.format("Group '%s' not found", groupName);
                throw new RuntimeException(errorMessage);
            }
            UserGroup userGroup = UserGroup.createMembership(user, group);
            this.userGroupRepository.save(userGroup);
        });
        if (!Arrays.asList(groupNames).contains(Group.Everyone)) {
            this.addToDefaultUserGroup(user);
        }
    }
}
