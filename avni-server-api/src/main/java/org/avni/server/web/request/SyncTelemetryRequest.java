package org.avni.server.web.request;

import org.joda.time.DateTime;
import org.avni.server.domain.JsonObject;
//import org.avni.server.domain.User;

public class SyncTelemetryRequest {
    private String uuid;
    private Long organisationId;
    private String syncStatus;
    private JsonObject entityStatus;
<<<<<<< Updated upstream
//    private User createdBy;
    private DateTime createdDateTime;
//    private User lastModifiedBy;
=======
    private DateTime createdDateTime;
>>>>>>> Stashed changes
    private DateTime lastModifiedDateTime;
    private DateTime syncStartTime;
    private DateTime syncEndTime;
    private String appVersion;
    private String androidVersion;
    private String deviceName;
    private JsonObject deviceInfo;
    private String syncSource;

    public JsonObject getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(JsonObject deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
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
//    public User getCreatedBy() {
//        return createdBy;
//    }

//    public void setCreatedBy(User createdBy) {
//        this.createdBy = createdBy;
//    }

=======
>>>>>>> Stashed changes
    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

<<<<<<< Updated upstream
//    public User getLastModifiedBy() {
//        return lastModifiedBy;
//    }

//    public void setLastModifiedBy(User lastModifiedBy) {
//        this.lastModifiedBy = lastModifiedBy;
//    }

=======
>>>>>>> Stashed changes
    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
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
}
