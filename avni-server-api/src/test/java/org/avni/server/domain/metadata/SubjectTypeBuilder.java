package org.avni.server.domain.metadata;

import org.avni.server.application.Subject;
import org.avni.server.domain.SubjectType;

import java.util.UUID;

public class SubjectTypeBuilder {
    private final SubjectType subjectType = new SubjectType();

    public SubjectTypeBuilder withUuid(String uuid) {
        subjectType.setUuid(uuid);
        return this;
    }

    public SubjectTypeBuilder withName(String name) {
        subjectType.setName(name);
        return this;
    }

    public SubjectTypeBuilder withMandatoryFieldsForNewEntity() {
        String s = UUID.randomUUID().toString();
        return withName(s).withUuid(s);
    }

    public SubjectTypeBuilder withType(Subject type) {
        subjectType.setType(type);
    	return this;
    }

    public SubjectType build() {
        return subjectType;
    }
}
