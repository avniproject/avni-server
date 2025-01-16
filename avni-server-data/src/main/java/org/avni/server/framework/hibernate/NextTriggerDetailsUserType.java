package org.avni.server.framework.hibernate;

import org.avni.messaging.domain.NextTriggerDetails;

public class NextTriggerDetailsUserType extends AbstractJsonbUserType<NextTriggerDetails> {
    @Override
    public Class returnedClass() {
        return NextTriggerDetails.class;
    }
}
