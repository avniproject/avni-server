package org.avni.server.domain.sync;

import java.util.Date;

public interface SubjectLinkedSyncEntity {
    void setSyncDisabledDateTime(Date syncDisabledDateTime);
    void setSyncDisabled(boolean syncDisabled);
}
