package org.avni.server.framework.hibernate;

import org.avni.server.application.KeyValues;

public class KeyValuesUserType extends AbstractJsonbUserType<KeyValues> {
    @Override
    public Class returnedClass() {
        return KeyValues.class;
    }
}
