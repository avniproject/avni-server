package org.avni.server.service.attendance;

import org.avni.server.domain.calendar.DayType;

public class ReasonRequiredException extends RuntimeException {
    public enum RequiredFor {
        DidntHappen,
        MarkAnywayHeld
    }

    private final DayType dayType;
    private final RequiredFor requiredFor;

    public ReasonRequiredException(DayType dayType, RequiredFor requiredFor) {
        super("ReasonRequired");
        this.dayType = dayType;
        this.requiredFor = requiredFor;
    }

    public DayType getDayType() {
        return dayType;
    }

    public RequiredFor getRequiredFor() {
        return requiredFor;
    }
}
