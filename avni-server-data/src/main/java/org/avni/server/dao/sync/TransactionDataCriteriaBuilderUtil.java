package org.avni.server.dao.sync;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import org.avni.server.dao.SyncParameters;

public class TransactionDataCriteriaBuilderUtil {
    public static Join<Object, Object> joinUserSubjectAssignment(From fromSubject) {
        return fromSubject.join("userSubjectAssignments");
    }

    public static Join<Object, Object> joinUserSubjectAssignmentViaSubject(From from) {
        return joinUserSubjectAssignment(from.join("individual"));
    }

    public static From joinSubjectForUserSubjectType(SyncParameters syncParameters, From from) {
        switch (syncParameters.getSyncEntityName()) {
            case Comment:
            case CommentThread:
                return from;
            case Encounter:
            case ProgramEnrolment:
            case ProgramEncounter:
                return from.join("individual");
            default:
                throw new RuntimeException("Unsupported sync entity name: " + syncParameters.getSyncEntityName());
        }
    }
}
