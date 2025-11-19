package org.avni.server.service.accessControl;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.constraints.NotNull;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.CatchmentService;
import org.avni.server.service.UserService;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AccessControlService {
    private final UserRepository userRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final PrivilegeRepository privilegeRepository;
    private final CatchmentService catchmentService;
    private final UserSubjectAssignmentService userSubjectAssignmentService;
    private final UserService userService;

    @Autowired
    public AccessControlService(UserRepository userRepository, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, PrivilegeRepository privilegeRepository, CatchmentService catchmentService, UserSubjectAssignmentService userSubjectAssignmentService, UserService userService) {
        this.userRepository = userRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.privilegeRepository = privilegeRepository;
        this.catchmentService = catchmentService;
        this.userSubjectAssignmentService = userSubjectAssignmentService;
        this.userService = userService;
    }

    public void checkPrivilege(PrivilegeType privilegeType) {
        this.checkPrivilege(UserContextHolder.getUser(), privilegeType);
    }

    public void checkOrgPrivilege(PrivilegeType privilegeType) {
        User user = UserContextHolder.getUser();
        if (userExistsAndHasAllPrivileges(user)) return;

        if (!userRepository.hasPrivilege(privilegeType.name(), user.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType);
        }
    }

    public void checkPrivilege(User contextUser, PrivilegeType privilegeType) {
        if (userExistsAndHasAllPrivileges(contextUser) || (contextUser.isAdmin() && privilegeRepository.isAllowedForAdmin(privilegeType))) return;

        if (!userRepository.hasPrivilege(privilegeType.name(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType);
        }
    }

    public void checkHasAnyOfSpecificPrivileges(List<PrivilegeType> privilegeTypes) {
        this.checkHasAnyOfSpecificPrivileges(UserContextHolder.getUser(), privilegeTypes);
    }

    public void checkHasAnyOfSpecificPrivileges(User contextUser, List<PrivilegeType> privilegeTypes) {
        if (userExistsAndHasAllPrivileges(contextUser) || (contextUser.isAdmin() && privilegeRepository.isAnyOfSpecificAllowedForAdmin(privilegeTypes))) return;

        List<String> privilegeTypeNames = privilegeTypes.stream().map(Enum::name).collect(Collectors.toList());
        if (!userRepository.hasAnyOfSpecificPrivileges(privilegeTypeNames, contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeTypes);
        }
    }
    private boolean userExistsAndHasAllPrivileges(User contextUser) {
        if (contextUser == null) throw new AvniNoUserSessionException("User not logged in");
        return userRepository.hasAllPrivileges(contextUser.getId());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, SubjectType subjectType) {
        this.checkSubjectPrivilege(privilegeType, subjectType.getUuid());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, GroupSubject groupSubject) {
        this.checkSubjectPrivilege(privilegeType, groupSubject.getGroupSubject());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, @NotNull String subjectTypeUUID) {
        this.checkSubjectPrivilege(UserContextHolder.getUser(), privilegeType, subjectTypeUUID);
    }

    public void checkHasAnyOfSpecificSubjectPrivileges(List<PrivilegeType> privilegeTypes, @NotNull String subjectTypeUUID) {
        this.checkHasAnyOfSpecificSubjectPrivileges(UserContextHolder.getUser(), privilegeTypes, subjectTypeUUID);
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, Individual individual) {
        this.checkSubjectPrivilege(UserContextHolder.getUser(), privilegeType, individual.getSubjectType().getUuid());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, List<String> subjectTypeUUIDs) {
        subjectTypeUUIDs.forEach(s -> this.checkSubjectPrivilege(UserContextHolder.getUser(), privilegeType, s));
    }

    public void checkSubjectPrivileges(PrivilegeType privilegeType, Individual... subjects) {
        this.checkSubjectPrivileges(privilegeType, Arrays.stream(subjects).collect(Collectors.toList()));
    }

    public void checkSubjectPrivileges(PrivilegeType privilegeType, List<Individual> subjects) {
        this.checkSubjectPrivilege(privilegeType, subjects.stream().map(Individual::getSubjectType).distinct().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
    }

    public void checkGroupSubjectPrivileges(PrivilegeType privilegeType, List<GroupSubject> groupSubjects) {
        this.checkSubjectPrivilege(privilegeType, groupSubjects.stream().map(groupSubject -> groupSubject.getGroupSubject().getSubjectType())
                .distinct().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
    }

    public void checkSubjectPrivilege(User contextUser, PrivilegeType privilegeType, @NotNull String subjectTypeUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        if (subjectType == null) return;
        if (!userRepository.hasSubjectPrivilege(privilegeType.name(), subjectType.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType, subjectTypeUUID, SubjectType.class);
        }
    }

    public void checkHasAnyOfSpecificSubjectPrivileges(User contextUser, List<PrivilegeType> privilegeTypes, @NotNull String subjectTypeUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        if (subjectType == null) return;
        if (!userRepository.hasAnyOfSpecificSubjectPrivileges(privilegeTypes.stream().map(Enum::name).collect(Collectors.toList()), subjectType.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeTypes);
        }
    }

    public void checkProgramPrivilege(PrivilegeType privilegeType, ProgramEnrolment programEnrolment) {
        checkProgramPrivilege(UserContextHolder.getUser(), privilegeType, programEnrolment.getProgram().getUuid());
    }

    public void checkProgramPrivilege(PrivilegeType privilegeType, @NotNull String programUUID) {
        this.checkProgramPrivilege(UserContextHolder.getUser(), privilegeType, programUUID);
    }

    public void checkProgramPrivileges(PrivilegeType privilegeType, List<ProgramEnrolment> programEnrolments) {
        this.checkProgramPrivilege(privilegeType, programEnrolments.stream().map(ProgramEnrolment::getProgram).distinct().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
    }

    public void checkProgramPrivilege(PrivilegeType privilegeType, List<String> programUUIDs) {
        programUUIDs.forEach(s -> this.checkProgramPrivilege(UserContextHolder.getUser(), privilegeType, s));
    }

    public void checkProgramPrivilege(User contextUser, PrivilegeType privilegeType, @NotNull String programUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        Program program = programRepository.findByUuid(programUUID);
        if (program == null) return;
        if (!userRepository.hasProgramPrivilege(privilegeType.name(), program.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType, programUUID, Program.class);
        }
    }

    public void checkProgramEncounterPrivilege(PrivilegeType privilegeType, ProgramEncounter programEncounter) {
        checkProgramEncounterPrivilege(UserContextHolder.getUser(), privilegeType, programEncounter.getEncounterType().getUuid());
    }

    public void checkProgramEncounterPrivilege(User contextUser, PrivilegeType privilegeType, @NotNull String encounterTypeUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUUID);
        if (encounterType == null) return;
        if (!userRepository.hasProgramEncounterPrivilege(privilegeType.name(), encounterType.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType, encounterTypeUUID, EncounterType.class);
        }
    }

    public boolean hasProgramEncounterPrivilege(@NotNull String encounterTypeUUID) {
        return this.hasProgramEncounterPrivilege(Objects.requireNonNull(UserContextHolder.getUser()), encounterTypeUUID);
    }

    public boolean hasProgramEncounterPrivilege(User contextUser, @NotNull String encounterTypeUUID) {
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUUID);
        return userRepository.hasProgramEncounterPrivilege(PrivilegeType.ViewVisit.name(), encounterType.getId(), contextUser.getId());
    }

    public void checkProgramEncounterPrivilege(PrivilegeType privilegeType, @NotNull String encounterTypeUUID) {
        this.checkProgramEncounterPrivilege(UserContextHolder.getUser(), privilegeType, encounterTypeUUID);
    }

    public void checkProgramEncounterPrivilege(PrivilegeType privilegeType, List<String> encounterTypeUUIDs) {
        encounterTypeUUIDs.forEach(s -> this.checkProgramEncounterPrivilege(UserContextHolder.getUser(), privilegeType, s));
    }

    public void checkProgramEncounterPrivileges(PrivilegeType privilegeType, List<ProgramEncounter> programEncounters) {
        this.checkProgramEncounterPrivilege(privilegeType, programEncounters.stream().map(ProgramEncounter::getEncounterType).distinct().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
    }

    public void checkEncounterPrivilege(PrivilegeType privilegeType, Encounter encounter) {
        checkEncounterPrivilege(UserContextHolder.getUser(), privilegeType, encounter.getEncounterType().getUuid());
    }

    public void checkEncounterPrivilege(PrivilegeType privilegeType, @NotNull String encounterTypeUUID) {
        this.checkEncounterPrivilege(UserContextHolder.getUser(), privilegeType, encounterTypeUUID);
    }

    public void checkEncounterPrivilege(User contextUser, PrivilegeType privilegeType, @NotNull String encounterTypeUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUUID);
        if (encounterType == null) return;
        if (!userRepository.hasEncounterPrivilege(privilegeType.name(), encounterType.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType, encounterTypeUUID, EncounterType.class);
        }
    }

    public void checkEncounterPrivilege(PrivilegeType privilegeType, List<String> encounterTypeUUIDs) {
        encounterTypeUUIDs.forEach(s -> this.checkEncounterPrivilege(UserContextHolder.getUser(), privilegeType, s));
    }

    public void checkEncounterPrivileges(PrivilegeType privilegeType, List<Encounter> encounters) {
        this.checkEncounterPrivilege(privilegeType, encounters.stream().map(Encounter::getEncounterType).distinct().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
    }

    public void assertIsSuperAdmin() {
        User user = UserContextHolder.getUser();
        if (!userService.isAdmin(user))
            throw AvniAccessException.createForNotAdmin(user);
    }

    public void assertIsNotSuperAdmin() {
        User user = UserContextHolder.getUser();
        if (userService.isAdmin(user))
            throw AvniAccessException.createForAdmin(user);
    }
    public void checkApprovePrivilegeOnEntityApprovalStatus(String entityType, String entityTypeUuid) {
        switch (EntityApprovalStatus.EntityType.valueOf(entityType)) {
            case Subject:
                this.checkSubjectPrivilege(PrivilegeType.ApproveSubject, entityTypeUuid);
                break;
            case Encounter:
            case ProgramEncounter:
                this.checkEncounterPrivilege(PrivilegeType.ApproveEncounter, entityTypeUuid);
                break;
            case ProgramEnrolment:
                this.checkProgramPrivilege(PrivilegeType.ApproveEnrolment, entityTypeUuid);
                break;
//            TODO: implement when ApproveChecklistitem privileges are enforced
//            case ChecklistItem:
        }
    }

    public void checkApprovePrivilegeOnEntityApprovalStatuses(List<EntityApprovalStatus> entityApprovalStatuses) {
        Map<List<String>, Long> uniqueEASByTypeAndTypeUuid = entityApprovalStatuses
                .stream()
                .collect(Collectors.groupingBy(entityApprovalStatus -> Arrays.asList(String.valueOf(entityApprovalStatus.getEntityType()), entityApprovalStatus.getEntityTypeUuid()), Collectors.counting()));

        uniqueEASByTypeAndTypeUuid.keySet().forEach(entity -> checkApprovePrivilegeOnEntityApprovalStatus(entity.get(0), entity.get(1)));
    }

    // Since an Individual can be saved multiple times in a single transaction, the flush can also happen in between, the method expects that the pre-save state is explicitly passed.
    public SubjectPartitionCheckStatus checkSubjectAccess(Individual subject, SubjectPartitionData previousPartitionState) {
        User currentUser = userService.getCurrentUser();
        boolean firstTimeCreation = previousPartitionState == null;
        SubjectType subjectType = subject.getSubjectType();
        SubjectPartitionData applicablePartitionData = firstTimeCreation ? SubjectPartitionData.create(subject) : previousPartitionState;

        if (subjectType.isShouldSyncByLocation() && !catchmentService.hasLocation(applicablePartitionData.getAddressLevel(), currentUser.getCatchment())) {
            return SubjectPartitionCheckStatus.failed(SubjectPartitionCheckStatus.NotInThisUsersCatchment);
        }

        if (subjectType.isDirectlyAssignable() && !firstTimeCreation && !userSubjectAssignmentService.isAssignedToUser(subject, currentUser)) {
            return SubjectPartitionCheckStatus.failed(SubjectPartitionCheckStatus.NotDirectlyAssignedToThisUser);
        }

        if (!currentUser.isIgnoreSyncSettingsInDEA() && subjectType.isAnySyncRegistrationConceptUsable()) {
            List<UserSyncSettings> syncSettingsList = getSyncSettingsList(currentUser.getSyncSettings());
            UserSyncSettings userSyncSettingsForSubjectType = syncSettingsList.stream().filter(userSyncSettings -> userSyncSettings.getSubjectTypeUUID().equals(subjectType.getUuid())).findFirst().orElse(null);
            if (userSyncSettingsForSubjectType == null) {
                return SubjectPartitionCheckStatus.failed(SubjectPartitionCheckStatus.UserSyncAttributeNotConfigured);
            }

            if (subjectType.getSyncRegistrationConcept1() != null && !userSyncSettingsForSubjectType.hasSync1Value(applicablePartitionData.getSync1ConceptValue())) {
                return SubjectPartitionCheckStatus.failed(SubjectPartitionCheckStatus.SyncAttributeForUserNotValidForUpdate);
            }

            if (subjectType.getSyncRegistrationConcept2() != null && !userSyncSettingsForSubjectType.hasSync1Value(applicablePartitionData.getSync2ConceptValue())) {
                return SubjectPartitionCheckStatus.failed(SubjectPartitionCheckStatus.SyncAttributeForUserNotValidForUpdate);
            }
        }

        return SubjectPartitionCheckStatus.passed();
    }

    private static List<UserSyncSettings> getSyncSettingsList(JsonObject syncSettings) {
        User.SyncSettingKeys.subjectTypeSyncSettings.name();
        return ObjectMapperSingleton.getObjectMapper().convertValue(syncSettings.get(User.SyncSettingKeys.subjectTypeSyncSettings.name()), new TypeReference<>() {
        });
    }
}
