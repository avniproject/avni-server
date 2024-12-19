package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "sync_telemetry")
@BatchSize(size = 100)
public class SyncTelemetry extends OrganisationAwareEntity{

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private String syncStatus;

    @Column
    @Type(value = JSONObjectUserType.class)
    private JsonObject entityStatus;

    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime syncStartTime;

    @Column
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime syncEndTime;

    @Column
    private String appVersion;

    @Column
    private String androidVersion;

    @Column
    private String deviceName;

    @Column
    @Type(value = JSONObjectUserType.class)
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
