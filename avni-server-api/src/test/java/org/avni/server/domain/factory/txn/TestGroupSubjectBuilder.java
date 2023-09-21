package org.avni.server.domain.factory.txn;

<<<<<<< HEAD
=======
import org.avni.server.domain.AddressLevel;
>>>>>>> 4.0
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
<<<<<<< HEAD
        entity.setGroupSubjectAddressId(subject.getAddressLevel().getId());
=======
        AddressLevel addressLevel = subject.getAddressLevel();
        if (addressLevel != null)
            entity.setGroupSubjectAddressId(addressLevel.getId());
>>>>>>> 4.0
    	return this;
    }

    public TestGroupSubjectBuilder withMember(Individual subject) {
        entity.setMemberSubject(subject);
<<<<<<< HEAD
        entity.setMemberSubjectAddressId(subject.getAddressLevel().getId());
=======
        AddressLevel addressLevel = subject.getAddressLevel();
        if (addressLevel != null)
            entity.setMemberSubjectAddressId(addressLevel.getId());
>>>>>>> 4.0
    	return this;
    }

    public TestGroupSubjectBuilder withGroupRole(GroupRole groupRole) {
        entity.setGroupRole(groupRole);
    	return this;
    }

<<<<<<< HEAD
=======
    public TestGroupSubjectBuilder setId(Long id) {
        entity.setId(id);
        return this;
    }

>>>>>>> 4.0
    public GroupSubject build() {
        return entity;
    }
}
