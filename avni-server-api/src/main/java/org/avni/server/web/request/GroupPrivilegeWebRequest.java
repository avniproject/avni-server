package org.avni.server.web.request;

public class GroupPrivilegeWebRequest {
    private String uuid;
    private Long subjectTypeId;
    private Long programId;
    private Long programEncounterTypeId;
    private Long encounterTypeId;
    private Long checklistDetailId;
    private boolean allow;
    private long privilegeId;
    private long groupId;

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

    public long getPrivilegeId() {
        return privilegeId;
    }

    public void setPrivilegeId(long privilegeId) {
        this.privilegeId = privilegeId;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public Long getSubjectTypeId() {
        return subjectTypeId;
    }

    public void setSubjectTypeId(Long subjectTypeId) {
        this.subjectTypeId = subjectTypeId;
    }

    public Long getProgramId() {
        return programId;
    }

    public void setProgramId(Long programId) {
        this.programId = programId;
    }

    public Long getProgramEncounterTypeId() {
        return programEncounterTypeId;
    }

    public void setProgramEncounterTypeId(Long programEncounterTypeId) {
        this.programEncounterTypeId = programEncounterTypeId;
    }

    public Long getEncounterTypeId() {
        return encounterTypeId;
    }

    public void setEncounterTypeId(Long encounterTypeId) {
        this.encounterTypeId = encounterTypeId;
    }

    public Long getChecklistDetailId() {
        return checklistDetailId;
    }

    public void setChecklistDetailId(Long checklistDetailId) {
        this.checklistDetailId = checklistDetailId;
    }
}
