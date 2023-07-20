package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "sync_telemetry")
@BatchSize(size = 100)
public class SyncTelemetry {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @Id
    private Long id;

    @Column
    @NotNull
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private Long organisationId;

    @Column
    private String syncStatus;

    @Column
    @Type(type = "jsonObject")
    private JsonObject entityStatus;

<<<<<<< Updated upstream
//    @JsonIgnore
//    @JoinColumn(name = "created_by_id")
//    @ManyToOne(targetEntity = User.class)
//    private User createdBy;

    private DateTime createdDateTime;

//    @JsonIgnore
//    @JoinColumn(name = "last_modified_by_id")
//    @ManyToOne(targetEntity = User.class)
//    private User lastModifiedBy;

=======
    private DateTime createdDateTime;

>>>>>>> Stashed changes
    private DateTime lastModifiedDateTime;

    @Column
    private DateTime syncStartTime;

    @Column
    private DateTime syncEndTime;

    @Column
    private String appVersion;

    @Column
    private String androidVersion;

    @Column
    private String deviceName;

    @Column
    @Type(type = "jsonObject")
    private JsonObject deviceInfo;

    @Column
    private String syncSource;

    public JsonObject getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(JsonObject deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public JsonObject getEntityStatus() {
        return entityStatus;
    }

    public void setEntityStatus(JsonObject entityStatus) {
        this.entityStatus = entityStatus;
    }

<<<<<<< Updated upstream
//    public void setCreatedBy(User createdBy) {
//        this.createdBy = createdBy;
//    }

=======
>>>>>>> Stashed changes
    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

<<<<<<< Updated upstream
//    public void setLastModifiedBy(User lastModifiedBy) {
//        this.lastModifiedBy = lastModifiedBy;
//    }

=======
>>>>>>> Stashed changes
    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }

<<<<<<< Updated upstream
//    public User getCreatedBy() { return createdBy; }

=======
>>>>>>> Stashed changes
    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

<<<<<<< Updated upstream
//    public User getLastModifiedBy() {
//        return lastModifiedBy;
//    }

=======
>>>>>>> Stashed changes
    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public DateTime getSyncStartTime() {
        return syncStartTime;
    }

    public void setSyncStartTime(DateTime syncStartTime) {
        this.syncStartTime = syncStartTime;
    }

    public DateTime getSyncEndTime() {
        return syncEndTime;
    }

    public void setSyncEndTime(DateTime syncEndTime) {
        this.syncEndTime = syncEndTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }

    public void setAndroidVersion(String androidVersion) {
        this.androidVersion = androidVersion;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getSyncSource() {
        return syncSource;
    }

    public void setSyncSource(String syncSource) {
        this.syncSource = syncSource;
    }

    public void setAuditInfo() {
        if (this.getCreatedDateTime() == null) {
            this.setCreatedDateTime(DateTime.now());
        }
        this.setLastModifiedDateTime(DateTime.now());
    }
}
