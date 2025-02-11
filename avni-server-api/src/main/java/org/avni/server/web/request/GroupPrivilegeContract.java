package org.avni.server.web.request;

import org.avni.server.domain.ChecklistDetail;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;

import java.util.Objects;
import java.util.Optional;

public class GroupPrivilegeContract {

    private Long groupPrivilegeId;
    private Long groupId;
    private Long privilegeId;
    private String privilegeEntityType;
    private String privilegeTypeName;
    private String privilegeName;
    private String privilegeDescription;
    private Long subjectTypeId;
    private String subjectTypeName;
    private Optional<Long> programId;
    private Optional<String> programName;
    private Optional<Long> programEncounterTypeId;
    private Optional<String> programEncounterTypeName;
    private Optional<Long> encounterTypeId;
    private Optional<String> encounterTypeName;
    private Optional<Long> checklistDetailId;
    private Optional<String> checklistDetailName;
    private boolean allow;
    private String uuid;

    public static GroupPrivilegeContract fromEntity(GroupPrivilege groupPrivilege) {
        GroupPrivilegeContract groupPrivilegeContract = new GroupPrivilegeContract();
        groupPrivilegeContract.setGroupPrivilegeId(groupPrivilege.getId());
        groupPrivilegeContract.setGroupId(groupPrivilege.getGroup().getId());
        groupPrivilegeContract.setPrivilegeId(groupPrivilege.getPrivilege().getId());
        groupPrivilegeContract.setPrivilegeEntityType(groupPrivilege.getPrivilege().getEntityType().name());
        groupPrivilegeContract.privilegeTypeName = groupPrivilege.getPrivilege().getType().name();
        groupPrivilegeContract.setPrivilegeName(groupPrivilege.getPrivilege().getName());
        groupPrivilegeContract.setPrivilegeDescription(groupPrivilege.getPrivilege().getDescription());
        groupPrivilegeContract.setSubjectTypeId(Optional.ofNullable(groupPrivilege.getSubjectType()).map(SubjectType::getId).orElse(null));
        groupPrivilegeContract.setSubjectTypeName(Optional.ofNullable(groupPrivilege.getSubjectType()).map(SubjectType::getName).orElse(null));
        groupPrivilegeContract.setProgramId(Optional.ofNullable(groupPrivilege.getProgram()).map(Program::getId));
        groupPrivilegeContract.setProgramName(Optional.ofNullable(groupPrivilege.getProgram()).map(Program::getName).orElse(null));
        groupPrivilegeContract.setProgramEncounterTypeId(Optional.ofNullable(groupPrivilege.getProgramEncounterType()).map(EncounterType::getId));
        groupPrivilegeContract.setProgramEncounterTypeName(Optional.ofNullable(groupPrivilege.getProgramEncounterType()).map(EncounterType::getName).orElse(null));
        groupPrivilegeContract.setEncounterTypeId(Optional.ofNullable(groupPrivilege.getEncounterType()).map(EncounterType::getId));
        groupPrivilegeContract.setEncounterTypeName(Optional.ofNullable(groupPrivilege.getEncounterType()).map(EncounterType::getName).orElse(null));
        groupPrivilegeContract.setChecklistDetailId(Optional.ofNullable(groupPrivilege.getChecklistDetail()).map(ChecklistDetail::getId));
        groupPrivilegeContract.setChecklistDetailName(Optional.ofNullable(groupPrivilege.getChecklistDetail()).map(ChecklistDetail::getName).orElse(null));
        groupPrivilegeContract.setAllow(groupPrivilege.isAllow());
        groupPrivilegeContract.setUuid(groupPrivilege.getUuid());
        return groupPrivilegeContract;
    }

    public Long getSubjectTypeId() {
        return subjectTypeId;
    }

    public void setSubjectTypeId(Long subjectTypeId) {
        this.subjectTypeId = subjectTypeId;
    }

    public Long getProgramId() {
        return programId == null ? null : programId.orElse(null);
    }

    public void setProgramId(Optional<Long> programId) {
        this.programId = programId;
    }

    public Long getProgramEncounterTypeId() {
        return programEncounterTypeId == null ? null : programEncounterTypeId.orElse(null);
    }

    public void setProgramEncounterTypeId(Optional<Long> programEncounterTypeId) {
        this.programEncounterTypeId = programEncounterTypeId;
    }

    public Long getEncounterTypeId() {
        return encounterTypeId == null ? null : encounterTypeId.orElse(null);
    }

    public void setEncounterTypeId(Optional<Long> encounterTypeId) {
        this.encounterTypeId = encounterTypeId;
    }

    public Long getChecklistDetailId() {
        return checklistDetailId == null ? null : checklistDetailId.orElse(null);
    }

    public void setChecklistDetailId(Optional<Long> checklistDetailId) {
        this.checklistDetailId = checklistDetailId;
    }

    public Long getGroupPrivilegeId() {
        return groupPrivilegeId;
    }

    public void setGroupPrivilegeId(Long groupPrivilegeId) {
        this.groupPrivilegeId = groupPrivilegeId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getPrivilegeId() {
        return privilegeId;
    }

    public void setPrivilegeId(Long privilegeId) {
        this.privilegeId = privilegeId;
    }

    public String getPrivilegeEntityType() {
        return privilegeEntityType;
    }

    public void setPrivilegeEntityType(String privilegeEntityType) {
        this.privilegeEntityType = privilegeEntityType;
    }

    public String getPrivilegeName() {
        return privilegeName;
    }

    public void setPrivilegeName(String privilegeName) {
        this.privilegeName = privilegeName;
    }

    public String getPrivilegeDescription() {
        return privilegeDescription;
    }

    public void setPrivilegeDescription(String privilegeDescription) {
        this.privilegeDescription = privilegeDescription;
    }

    public String getSubjectTypeName() {
        return subjectTypeName;
    }

    public void setSubjectTypeName(String subjectTypeName) {
        this.subjectTypeName = subjectTypeName;
    }

    public String getProgramName() {
        return programName == null ? null : programName.orElse(null);
    }

    public void setProgramName(String programName) {
        this.programName = Optional.ofNullable(programName);
    }

    public String getProgramEncounterTypeName() {
        return programEncounterTypeName == null ? null : programEncounterTypeName.orElse(null);
    }

    public void setProgramEncounterTypeName(String programEncounterTypeName) {
        this.programEncounterTypeName = Optional.ofNullable(programEncounterTypeName);
    }

    public String getEncounterTypeName() {
        return encounterTypeName == null ? null : encounterTypeName.orElse(null);
    }

    public void setEncounterTypeName(String encounterTypeName) {
        this.encounterTypeName = Optional.ofNullable(encounterTypeName);
    }

    public String getChecklistDetailName() {
        return checklistDetailName == null ? null : checklistDetailName.orElse(null);
    }

    public void setChecklistDetailName(String checklistDetailName) {
        this.checklistDetailName = Optional.ofNullable(checklistDetailName);
    }

    public boolean isAllow() {
        return allow;
    }

    public void setAllow(boolean allow) {
        this.allow = allow;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPrivilegeTypeName() {
        return privilegeTypeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupPrivilegeContract that = (GroupPrivilegeContract) o;
        return groupId.equals(that.groupId) &&
                privilegeId.equals(that.privilegeId) &&
                Objects.equals(subjectTypeId, that.subjectTypeId) &&
                Objects.equals(programId, that.programId) &&
                Objects.equals(programEncounterTypeId, that.programEncounterTypeId) &&
                Objects.equals(encounterTypeId, that.encounterTypeId) &&
                Objects.equals(checklistDetailId, that.checklistDetailId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, privilegeId, subjectTypeId, programId, programEncounterTypeId, encounterTypeId, checklistDetailId);
    }
}
