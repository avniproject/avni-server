package org.avni.server.domain.metadata;

import org.avni.server.domain.SubjectType;

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

    public SubjectType build() {
        return subjectType;
    }
}
