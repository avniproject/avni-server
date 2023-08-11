package org.avni.server.domain.factory.txn;

import org.avni.server.domain.GroupRole;
import org.avni.server.domain.SubjectType;

import java.util.UUID;

public class TestGroupRoleBuilder {
    private final GroupRole entity = new GroupRole();

    public TestGroupRoleBuilder withRole(String role) {
        entity.setRole(role);
        return this;
    }

    public TestGroupRoleBuilder withGroupSubjectType(SubjectType subjectType) {
        entity.setGroupSubjectType(subjectType);
        return this;
    }

    public TestGroupRoleBuilder withMemberSubjectType(SubjectType subjectType) {
        entity.setMemberSubjectType(subjectType);
        return this;
    }

    public TestGroupRoleBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestGroupRoleBuilder withMaxNumberOfMembers(long max) {
        entity.setMaximumNumberOfMembers(max);
    	return this;
    }

    public TestGroupRoleBuilder withMinNumberOfMembers(long min) {
        entity.setMinimumNumberOfMembers(min);
    	return this;
    }

    public TestGroupRoleBuilder withMandatoryFieldsForNewEntity() {
        String uuid = UUID.randomUUID().toString();
        return withUuid(uuid).withRole(uuid).withMaxNumberOfMembers(10).withMinNumberOfMembers(0);
    }

    public GroupRole build() {
        return entity;
    }
}
