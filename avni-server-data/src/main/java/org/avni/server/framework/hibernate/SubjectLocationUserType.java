package org.avni.server.framework.hibernate;

import org.avni.server.domain.SubjectLocation;

public class SubjectLocationUserType extends AbstractJsonbUserType<SubjectLocation> {
    @Override
    public Class returnedClass() {
        return SubjectLocation.class;
    }
}