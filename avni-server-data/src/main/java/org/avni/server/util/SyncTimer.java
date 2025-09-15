package org.avni.server.util;

public class SyncTimer {
    private final long durationInMillis;
    private long startTime;

    private SyncTimer(long durationInMillis) {
        this.durationInMillis = durationInMillis;
    }

    public static SyncTimer fromMinutes(int durationInMinutes) {
        return new SyncTimer((long) durationInMinutes * 60 * 1000);
    }

    public static SyncTimer fromMillis(int durationInMillis) {
        return new SyncTimer(durationInMillis);
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    private long getTimeSpent() {
        return System.currentTimeMillis() - startTime;
    }

    public long getTimeLeft() {
        return durationInMillis - getTimeSpent();
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
