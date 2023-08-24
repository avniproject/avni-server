package org.avni.server.domain.factory.txn;

import org.avni.server.domain.GroupRole;
import org.avni.server.domain.GroupSubject;
import org.avni.server.domain.Individual;

import java.util.UUID;

public class TestGroupSubjectBuilder {
    private final GroupSubject entity = new GroupSubject();

    public TestGroupSubjectBuilder() {
        withUuid(UUID.randomUUID().toString());
    }

    public TestGroupSubjectBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestGroupSubjectBuilder withGroup(Individual subject) {
        entity.setGroupSubject(subject);
        entity.setGroupSubjectAddressId(subject.getAddressLevel().getId());
    	return this;
    }

    public TestGroupSubjectBuilder withMember(Individual subject) {
        entity.setMemberSubject(subject);
        entity.setMemberSubjectAddressId(subject.getAddressLevel().getId());
    	return this;
    }

    public TestGroupSubjectBuilder withGroupRole(GroupRole groupRole) {
        entity.setGroupRole(groupRole);
    	return this;
    }

    public GroupSubject build() {
        return entity;
    }
}
