package org.avni.server.web.request;

import org.joda.time.DateTime;

/**
 * One row of GET /user/activities?organisationId=...
 * Used by snapshot-server's scheduler to order users within an org by
 * recent-sync activity (descending), with uuid as a deterministic
 * tiebreak. lastSyncedAt is the max sync_telemetry.sync_end_time per user;
 * null when the user has never synced.
 */
public class UserActivity {
    private Long id;
    private String uuid;
    private String username;
    private String lastSyncedAt;

    public UserActivity() {}

    // JPA constructor-expression target.
    public UserActivity(Long id, String uuid, String username, DateTime lastSyncedAt) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.lastSyncedAt = lastSyncedAt == null ? null : lastSyncedAt.toString();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(String lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
