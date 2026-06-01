package org.avni.server.web.request;

/**
 * One row of GET /organisation/sqliteSnapshotEnabled. snapshot-server's
 * scheduler reads this list at the start of each tick to enumerate orgs
 * opted in via OrganisationConfig.enableSqliteSnapshotGeneration. Default
 * ordering on the wire is enabledAt asc (oldest-opted-in first), but
 * snapshot-server sorts client-side too so callers can re-order.
 */
public class SnapshotEnabledOrg {
    private Long id;
    private String name;
    private String dbUser;
    private String mediaDirectory;
    private String enabledAt;

    public SnapshotEnabledOrg() {}

    public SnapshotEnabledOrg(Long id, String name, String dbUser, String mediaDirectory, String enabledAt) {
        this.id = id;
        this.name = name;
        this.dbUser = dbUser;
        this.mediaDirectory = mediaDirectory;
        this.enabledAt = enabledAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDbUser() { return dbUser; }
    public void setDbUser(String dbUser) { this.dbUser = dbUser; }

    public String getMediaDirectory() { return mediaDirectory; }
    public void setMediaDirectory(String mediaDirectory) { this.mediaDirectory = mediaDirectory; }

    public String getEnabledAt() { return enabledAt; }
    public void setEnabledAt(String enabledAt) { this.enabledAt = enabledAt; }
}
