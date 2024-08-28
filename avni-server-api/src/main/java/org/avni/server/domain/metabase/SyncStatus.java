package org.avni.server.domain.metabase;

public enum SyncStatus {
    COMPLETE("complete"),
    INCOMPLETE("incomplete");

    private final String status;

    SyncStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public static SyncStatus fromString(String status) {
        for (SyncStatus s : SyncStatus.values()) {
            if (s.getStatus().equalsIgnoreCase(status)) {
                return s;
            }
        }
        return null;
    }

}
