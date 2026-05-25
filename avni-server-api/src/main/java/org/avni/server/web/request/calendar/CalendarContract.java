package org.avni.server.web.request.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.web.request.CHSRequest;
import org.joda.time.DateTime;

import java.util.Map;

public class CalendarContract extends CHSRequest {
    private String name;
    private Map<String, Object> workingPattern;
    private String addressLevelUUID;
    private boolean isDefault;
    private DateTime createdDateTime;
    private DateTime lastModifiedDateTime;

    public static CalendarContract fromEntity(Calendar calendar) {
        CalendarContract contract = new CalendarContract();
        contract.setId(calendar.getId());
        contract.setUuid(calendar.getUuid());
        contract.setVoided(calendar.isVoided());
        contract.setName(calendar.getName());
        contract.setWorkingPattern(calendar.getWorkingPattern());
        contract.setAddressLevelUUID(calendar.getAddressLevelUUID());
        contract.setDefault(calendar.isDefault());
        contract.setCreatedDateTime(calendar.getCreatedDateTime());
        contract.setLastModifiedDateTime(calendar.getLastModifiedDateTime());
        return contract;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getWorkingPattern() {
        return workingPattern;
    }

    public void setWorkingPattern(Map<String, Object> workingPattern) {
        this.workingPattern = workingPattern;
    }

    public String getAddressLevelUUID() {
        return addressLevelUUID;
    }

    public void setAddressLevelUUID(String addressLevelUUID) {
        this.addressLevelUUID = addressLevelUUID;
    }

    @JsonProperty("isDefault")
    public boolean isDefault() {
        return isDefault;
    }

    @JsonProperty("isDefault")
    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public DateTime getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(DateTime createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public void setLastModifiedDateTime(DateTime lastModifiedDateTime) {
        this.lastModifiedDateTime = lastModifiedDateTime;
    }
}
