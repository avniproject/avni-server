package org.avni.server.domain.factory.access;

import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;

import java.util.UUID;

public class TestGroupPrivilegeBuilder {
    private final GroupPrivilege entity = new GroupPrivilege();

    public TestGroupPrivilegeBuilder() {
        entity.setUuid(UUID.randomUUID().toString());
    }

    public TestGroupPrivilegeBuilder setUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestGroupPrivilegeBuilder setGroup(Group group) {
        entity.setGroup(group);
        return this;
    }

    public TestGroupPrivilegeBuilder setPrivilege(Privilege privilege) {
        entity.setPrivilege(privilege);
        return this;
    }

    public TestGroupPrivilegeBuilder setSubjectType(SubjectType subjectType) {
        entity.setSubjectType(subjectType);
        return this;
    }

    public TestGroupPrivilegeBuilder setProgram(Program program) {
        entity.setProgram(program);
        return this;
    }

    public TestGroupPrivilegeBuilder setProgramEncounterType(EncounterType programEncounterType) {
        entity.setProgramEncounterType(programEncounterType);
        return this;
    }

    public TestGroupPrivilegeBuilder setEncounterType(EncounterType encounterType) {
        entity.setEncounterType(encounterType);
        return this;
    }

    public TestGroupPrivilegeBuilder setChecklistDetail(ChecklistDetail checklistDetail) {
        entity.setChecklistDetail(checklistDetail);
        return this;
    }

    public TestGroupPrivilegeBuilder setAllow(boolean allow) {
        entity.setAllow(allow);
        return this;
    }

    public TestGroupPrivilegeBuilder withDefaultValuesForNewEntity() {
        return setAllow(true);
    }

    public GroupPrivilege build() {
        return entity;
    }
}
