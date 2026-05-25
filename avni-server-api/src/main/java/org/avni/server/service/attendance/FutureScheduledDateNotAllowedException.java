package org.avni.server.service.attendance;

import org.joda.time.LocalDate;

public class FutureScheduledDateNotAllowedException extends RuntimeException {
    private final LocalDate scheduledDate;

    public FutureScheduledDateNotAllowedException(LocalDate scheduledDate) {
        super("FutureScheduledDateNotAllowed");
        this.scheduledDate = scheduledDate;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }
}
