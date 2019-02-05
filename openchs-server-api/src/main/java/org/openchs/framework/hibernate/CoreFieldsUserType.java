package org.openchs.framework.hibernate;

import org.openchs.application.CoreFields;

public class CoreFieldsUserType extends AbstractJsonbUserType {
    @Override
    public Class returnedClass() {
        return CoreFields.class;
    }
}