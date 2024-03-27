package org.avni.server.util;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;

public class TimeTakenLogger {
    private final String message;
    private final Logger logger;
    private final Instant startTime;

    public TimeTakenLogger(String message, Logger logger) {
        this.message = message;
        this.logger = logger;
        this.startTime = Instant.now();
    }

    public void logInfo() {
        Duration between = Duration.between(this.startTime, Instant.now());
        this.logger.info("({} ms) {}", between.toMillis(), message);
    }
}
