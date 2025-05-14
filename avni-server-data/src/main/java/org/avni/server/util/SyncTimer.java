package org.avni.server.util;

public class SyncTimer {
    private final int durationInMinutes;
    private long startTime;

    public SyncTimer(int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    private long getTimeSpent() {
        return System.currentTimeMillis() - startTime;
    }

    public long getTimeLeft() {
        return ((long) durationInMinutes * 60 * 1000) - getTimeSpent();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("startTime=").append(startTime);
        sb.append(", timeSpent=").append(getTimeSpent());
        sb.append(", timeLeft=").append(getTimeLeft());
        sb.append('}');
        return sb.toString();
    }
}
