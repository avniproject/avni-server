package org.avni.server.dao.sync;

import org.avni.server.dao.SyncParameters;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;

public class TransactionDataCriteriaBuilderUtil {
    public static Join<Object, Object> joinUserSubjectAssignment(From fromSubject) {
        return fromSubject.join("userSubjectAssignments");
    }

    public static Join<Object, Object> joinUserSubjectAssignmentViaSubject(From from) {
        return joinUserSubjectAssignment(from.join("individual"));
    }

    public static Join<Object, Object> joinSubjectForUserSubjectType(SyncParameters syncParameters, From from) {
        switch (syncParameters.getSyncEntityName()) {
            case Comment:
                return from.join("subject");
            case CommentThread:
                return from.join("comments").join("subject");
            case Encounter:
            case ProgramEnrolment:
            case ProgramEncounter:
                return from.join("individual");
            default:
                throw new RuntimeException("Unsupported sync entity name: " + syncParameters.getSyncEntityName());
        }
    }
}
