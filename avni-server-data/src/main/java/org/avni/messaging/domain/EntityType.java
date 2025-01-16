package org.avni.messaging.domain;

public enum EntityType {
    Subject,
    ProgramEnrolment,
    Encounter,
    ProgramEncounter,
    User;

     public static boolean isCHSEntityType(EntityType entityType) {
        switch (entityType) {
            case Subject:
            case ProgramEnrolment:
            case Encounter:
            case ProgramEncounter:
                return true;
            case User:
            default:
                return false;
        }
    }
}
