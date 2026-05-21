package org.avni.server.web.request.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.web.request.CHSRequest;
import org.joda.time.DateTime;

import java.time.LocalDate;

public class CalendarDateMarkerContract extends CHSRequest {
    private String calendarUUID;
    private LocalDate markerDate;
    private String name;
    private boolean isWorking;
    private DateTime createdDateTime;
    private DateTime lastModifiedDateTime;

    public static CalendarDateMarkerContract fromEntity(CalendarDateMarker marker) {
        CalendarDateMarkerContract contract = new CalendarDateMarkerContract();
        contract.setId(marker.getId());
        contract.setUuid(marker.getUuid());
        contract.setVoided(marker.isVoided());
        contract.setCalendarUUID(marker.getCalendarUUID());
        contract.setMarkerDate(marker.getMarkerDate());
        contract.setName(marker.getName());
        contract.setWorking(marker.isWorking());
        contract.setCreatedDateTime(marker.getCreatedDateTime());
        contract.setLastModifiedDateTime(marker.getLastModifiedDateTime());
        return contract;
    }

    public String getCalendarUUID() {
        return calendarUUID;
    }

    public void setCalendarUUID(String calendarUUID) {
        this.calendarUUID = calendarUUID;
    }

    public LocalDate getMarkerDate() {
        return markerDate;
    }

    public void setMarkerDate(LocalDate markerDate) {
        this.markerDate = markerDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("isWorking")
    public boolean isWorking() {
        return isWorking;
    }

    @JsonProperty("isWorking")
    public void setWorking(boolean working) {
        isWorking = working;
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
