package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.exception.GroupNotFoundException;
import org.avni.server.util.PhoneNumberUtil;
import org.avni.server.web.validation.ValidationException;
import org.bouncycastle.util.Strings;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService implements NonScopeAwareService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserSubjectRepository userSubjectRepository;
    private final IndividualRepository individualRepository;
    private final SubjectTypeRepository subjectTypeRepository;

    @Autowired
    public UserService(UserRepository userRepository, GroupRepository groupRepository, UserGroupRepository userGroupRepository, UserSubjectRepository userSubjectRepository, IndividualRepository individualRepository, SubjectTypeRepository subjectTypeRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.userGroupRepository = userGroupRepository;
        this.userSubjectRepository = userSubjectRepository;
        this.individualRepository = individualRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    public User getCurrentUser() {
        UserContext userContext = UserContextHolder.getUserContext();
        return userContext.getUser();
    }

    public User save(User user) {
        String idPrefix = UserSettings.getIdPrefix(user.getSettings());
        if (StringUtils.hasLength(idPrefix)) {
            synchronized (String.format("%d-USER-ID-PREFIX-%s", user.getOrganisationId(), idPrefix).intern()) {
                List<User> usersWithSameIdPrefix = userRepository.getUsersWithSameIdPrefix(idPrefix, user.getId());
                if (usersWithSameIdPrefix.size() == 0) {
                    return createUpdateUser(user);
                } else {
                    throw new ValidationException(String.format("There is another user %s with same prefix: %s", usersWithSameIdPrefix.get(0).getUsername(), idPrefix));
                }
            }
        } else {
            return createUpdateUser(user);
        }
    }

    private User createUpdateUser(User user) {
        SubjectType userSubjectType = subjectTypeRepository.findByTypeAndIsVoidedFalse(Subject.User);
        User savedUser = userRepository.save(user);
        if (userSubjectType != null)
            this.ensureSubjectForUser(user, userSubjectType);
        return savedUser;
    }

    @Transactional
    public void addToDefaultUserGroup(User user) {
        if (user.getOrganisationId() != null) {
            Group group = groupRepository.findByNameAndOrganisationId(Group.Everyone, user.getOrganisationId());
            if (userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, group) == null) {
                UserGroup userGroup = UserGroup.createMembership(user, group);
                userGroupRepository.save(userGroup);
            }
        }
    }

    @Transactional
    public void associateUserToGroups(User user, List<Long> associatedGroupIds) {
        if(associatedGroupIds == null) {
            return;
        }
        List<UserGroup> userGroupsToBeSaved = new ArrayList<>();
        Group everyoneGroup = groupRepository.findByNameAndOrganisationId(Group.Everyone, user.getOrganisationId());
        List<Long> currentlyLinkedGroups = user.getUserGroups().stream()
                .map(UserGroup::getGroupId).collect(Collectors.toList());

        //Create new UserGroups for newly associated groups
        associatedGroupIds.stream()
                .filter(gid -> !currentlyLinkedGroups.contains(gid) && !everyoneGroup.getId().equals(gid))
                .forEach(groupId -> {
            Group group = this.groupRepository.findById(groupId)
                    .orElseThrow(GroupNotFoundException::new);
            userGroupsToBeSaved.add(UserGroup.createMembership(user, group));
        });

        //Update voided flag for UserGroups that already exist
        user.getUserGroups().stream()
                .forEach(userGroup -> {
            userGroup.setVoided(!associatedGroupIds
                    .contains(userGroup.getGroupId()) && !everyoneGroup.getId().equals(userGroup.getGroupId()));
            userGroupsToBeSaved.add(userGroup);
        });

        this.userGroupRepository.saveAll(userGroupsToBeSaved);
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
        return userRepository.findByPhoneNumber(PhoneNumberUtil.getStandardFormatPhoneNumber(phoneNumber));
    }

    @Transactional
    public void addToGroups(User user, String groupsSpecified) {
        if (groupsSpecified == null) {
            this.addToDefaultUserGroup(user);
            return;
        }

        String[] groupNames = Strings.split(groupsSpecified, ',');
        Arrays.stream(groupNames).distinct().forEach(groupName -> {
            if (!StringUtils.hasLength(groupName.trim())) return;

            Group group = this.groupRepository.findByName(groupName);
            if (group == null) {
                String errorMessage = String.format("Group '%s' not found", groupName);
                throw new RuntimeException(errorMessage);
            }
            if (userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, group) == null) {
                UserGroup userGroup = UserGroup.createMembership(user, group);
                this.userGroupRepository.save(userGroup);
            }
        });
        if (!Arrays.asList(groupNames).contains(Group.Everyone)) {
            this.addToDefaultUserGroup(user);
        }
    }

    public void ensureSubjectForUser(User user, SubjectType subjectType) {
        if (!subjectType.getType().equals(Subject.User))
            throw new RuntimeException(String.format("Subject type: %s is not of User type", subjectType.getType()));

        UserSubject userSubject = userSubjectRepository.findByUser(user);
        Individual subject;
        if (userSubject == null) {
            userSubject = new UserSubject();
            subject = new Individual();
            subject.setSubjectType(subjectType);
            subject.setFirstName(user.getName());
            subject.setOrganisationId(subjectType.getOrganisationId());
            subject.setRegistrationDate(user.getCreatedDateTime().toLocalDate());
            subject.assignUUID();

            userSubject.setSubject(subject);
            userSubject.setUser(user);
            userSubject.assignUUID();
            userSubject.setOrganisationId(subjectType.getOrganisationId());
        } else {
            subject = userSubject.getSubject();
        }

        subject.setVoided(false);
        userSubject.setVoided(false);

        individualRepository.save(subject);
        userSubjectRepository.save(userSubject);
    }

    public void setPhoneNumber(String phoneNumber, User user) {
        if (!PhoneNumberUtil.isValidPhoneNumber(phoneNumber)) {
            throw new ValidationException(PhoneNumberUtil.getInvalidMessage(phoneNumber));
        }
        user.setPhoneNumber(PhoneNumberUtil.getStandardFormatPhoneNumber(phoneNumber));
    }
}
