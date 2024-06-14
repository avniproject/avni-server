package org.avni.server.domain.metabase;

public class Group {
    private int id;
    private boolean hasPermission;

    public Group(int id, boolean hasPermission) {
        this.id = id;
        this.hasPermission = hasPermission;
    }

    public int getId() {
        return id;
    }

    public boolean hasPermission() {
        return hasPermission;
    }

    public void setPermission(boolean hasPermission) {
        this.hasPermission = hasPermission;
    }
}
