package org.avni.server.service.accessControl;

import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;

@Service
public class AccessControlService {
    private final UserRepository userRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;

    @Autowired
    public AccessControlService(UserRepository userRepository, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository) {
        this.userRepository = userRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
    }

    public void checkPrivilege(PrivilegeType privilegeType) {
        this.checkPrivilege(UserContextHolder.getUser(), privilegeType);
    }

    public void checkPrivilege(User contextUser, PrivilegeType privilegeType) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;

        if (!userRepository.hasPrivilege(privilegeType.name(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType);
        }
    }

    private boolean userExistsAndHasAllPrivileges(User contextUser) {
        if (contextUser == null) throw AvniAccessException.createNoUserException();
        return userRepository.hasAllPrivileges(contextUser.getId());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, SubjectType subjectType) {
        this.checkSubjectPrivilege(privilegeType, subjectType.getUuid());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, @NotNull String subjectTypeUUID) {
        this.checkSubjectPrivilege(UserContextHolder.getUser(), privilegeType, subjectTypeUUID);
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, Individual individual) {
        this.checkSubjectPrivilege(UserContextHolder.getUser(), privilegeType, individual.getSubjectType().getUuid());
    }

    public void checkSubjectPrivilege(PrivilegeType privilegeType, List<String> subjectTypeUUIDs) {
        subjectTypeUUIDs.forEach(s -> this.checkSubjectPrivilege(UserContextHolder.getUser(), privilegeType, s));
    }

    public void checkSubjectPrivilege(User contextUser, PrivilegeType privilegeType, @NotNull String subjectTypeUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        if (subjectType == null) return;
        if (!userRepository.hasSubjectPrivilege(privilegeType.name(), subjectType.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType, subjectTypeUUID, SubjectType.class);
        }
    }

    public void checkProgramPrivilege(PrivilegeType privilegeType, ProgramEnrolment programEnrolment) {
        checkProgramPrivilege(UserContextHolder.getUser(), privilegeType, programEnrolment.getProgram().getUuid());
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

    public void checkEncounterPrivilege(PrivilegeType privilegeType, Encounter encounter) {
        checkEncounterPrivilege(UserContextHolder.getUser(), privilegeType, encounter.getEncounterType().getUuid());
    }

    public void checkEncounterPrivilege(User contextUser, PrivilegeType privilegeType, @NotNull String encounterTypeUUID) {
        if (userExistsAndHasAllPrivileges(contextUser)) return;
        EncounterType encounterType = encounterTypeRepository.findByUuid(encounterTypeUUID);
        if (encounterType == null) return;
        if (!userRepository.hasEncounterPrivilege(privilegeType.name(), encounterType.getId(), contextUser.getId())) {
            throw AvniAccessException.createNoPrivilegeException(privilegeType, encounterTypeUUID, EncounterType.class);
        }
    }
}
