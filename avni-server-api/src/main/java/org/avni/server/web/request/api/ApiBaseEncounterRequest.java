package org.avni.server.web.request.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.joda.time.DateTime;
import org.avni.server.geo.Point;

import java.util.LinkedHashMap;

import static org.avni.server.web.api.CommonFieldNames.VOIDED;

public abstract class ApiBaseEncounterRequest {
    public static final String EXTERNAL_ID = "External ID";
    public static final String ENCOUNTER_TYPE = "Encounter type";
    public static final String ENCOUNTER_LOCATION = "Encounter Coordinates";
    public static final String CANCEL_LOCATION = "Cancel location";
    public static final String ENCOUNTER_DATE_TIME = "Encounter date time";
    public static final String EARLIEST_SCHEDULED_DATE = "Earliest scheduled date";
    public static final String MAX_SCHEDULED_DATE = "Max scheduled date";
    public static final String CANCEL_DATE_TIME = "Cancel date time";
    public static final String OBSERVATIONS = "observations";
    public static final String CANCEL_OBSERVATIONS = "cancelObservations";

    @JsonProperty(EXTERNAL_ID)
    private String externalId;

    @JsonProperty(ENCOUNTER_TYPE)
    private String encounterType;

    @JsonProperty(ENCOUNTER_LOCATION)
    private Point encounterLocation;

    @JsonProperty(CANCEL_LOCATION)
    private Point cancelLocation;

    @JsonProperty(ENCOUNTER_DATE_TIME)
    private DateTime encounterDateTime;

    @JsonProperty(EARLIEST_SCHEDULED_DATE)
    private DateTime earliestScheduledDate;

    @JsonProperty(MAX_SCHEDULED_DATE)
    private DateTime maxScheduledDate;

    @JsonProperty(CANCEL_DATE_TIME)
    private DateTime cancelDateTime;

    @JsonProperty(OBSERVATIONS)
    private LinkedHashMap<String, Object> observations;

    @JsonProperty(CANCEL_OBSERVATIONS)
    private LinkedHashMap<String, Object> cancelObservations;

    @JsonProperty(VOIDED)
    private boolean voided;

    public String getEncounterType() {
        return encounterType;
    }

    public void setEncounterType(String encounterType) {
        this.encounterType = encounterType;
    }

    public Point getEncounterLocation() {
        return encounterLocation;
    }

    public Point getCancelLocation() {
        return cancelLocation;
    }

    public DateTime getEncounterDateTime() {
        return encounterDateTime;
    }

    public void setEncounterDateTime(DateTime encounterDateTime) {
        this.encounterDateTime = encounterDateTime;
    }

    public DateTime getEarliestScheduledDate() {
        return earliestScheduledDate;
    }

    public DateTime getMaxScheduledDate() {
        return maxScheduledDate;
    }

    public DateTime getCancelDateTime() {
        return cancelDateTime;
    }

    public void setCancelDateTime(DateTime cancelDateTime) {
        this.cancelDateTime = cancelDateTime;
    }

    public LinkedHashMap<String, Object> getObservations() {
        return observations;
    }

    public void setObservations(LinkedHashMap<String, Object> observations) {
        this.observations = observations;
    }

    public LinkedHashMap<String, Object> getCancelObservations() {
        return cancelObservations;
    }

    public void setCancelObservations(LinkedHashMap<String, Object> cancelObservations) {
        this.cancelObservations = cancelObservations;
    }

    public boolean isVoided() {
        return voided;
    }

    public String getExternalId() { return externalId; }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
