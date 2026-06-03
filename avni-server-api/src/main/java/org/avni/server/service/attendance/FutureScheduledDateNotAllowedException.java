package org.avni.server.service.attendance;

import java.time.LocalDate;

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
