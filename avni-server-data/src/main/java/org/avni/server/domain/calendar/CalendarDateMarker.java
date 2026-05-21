package org.avni.server.domain.calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.OrganisationAwareEntity;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;

@Entity
@Table(name = "calendar_date_marker")
@BatchSize(size = 100)
@JsonIgnoreProperties({"calendar"})
public class CalendarDateMarker extends OrganisationAwareEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id")
    private Calendar calendar;

    @NotNull
    @Column(name = "marker_date")
    private LocalDate markerDate;

    @Column
    private String name;

    @Column(name = "is_working")
    private boolean isWorking;

    public Calendar getCalendar() {
        return calendar;
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
    }

    public String getCalendarUUID() {
        return calendar == null ? null : calendar.getUuid();
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
}
