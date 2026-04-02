package org.avni.server.web.request.reports;

import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.domain.ValueUnit;

import java.util.List;

public abstract class ReportCardRequest extends ReportCardContract {
    private List<String> standardReportCardInputSubjectTypes;
    private List<String> standardReportCardInputPrograms;
    private List<String> standardReportCardInputEncounterTypes;
    private ValueUnit standardReportCardInputRecentDuration;
    private String action;
    private String actionEncounterTypeUUID;
    private String onActionCompletion;

    public List<String> getStandardReportCardInputSubjectTypes() {
        return standardReportCardInputSubjectTypes;
    }

    public void setStandardReportCardInputSubjectTypes(List<String> standardReportCardInputSubjectTypes) {
        this.standardReportCardInputSubjectTypes = standardReportCardInputSubjectTypes;
    }

    public List<String> getStandardReportCardInputPrograms() {
        return standardReportCardInputPrograms;
    }

    public void setStandardReportCardInputPrograms(List<String> standardReportCardInputPrograms) {
        this.standardReportCardInputPrograms = standardReportCardInputPrograms;
    }

    public List<String> getStandardReportCardInputEncounterTypes() {
        return standardReportCardInputEncounterTypes;
    }

    public void setStandardReportCardInputEncounterTypes(List<String> standardReportCardInputEncounterTypes) {
        this.standardReportCardInputEncounterTypes = standardReportCardInputEncounterTypes;
    }

    public ValueUnit getStandardReportCardInputRecentDuration() {
        return standardReportCardInputRecentDuration;
    }

    public void setStandardReportCardInputRecentDuration(ValueUnit standardReportCardInputRecentDuration) {
        this.standardReportCardInputRecentDuration = standardReportCardInputRecentDuration;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionEncounterTypeUUID() {
        return actionEncounterTypeUUID;
    }

    public void setActionEncounterTypeUUID(String actionEncounterTypeUUID) {
        this.actionEncounterTypeUUID = actionEncounterTypeUUID;
    }

    public String getOnActionCompletion() {
        return onActionCompletion;
    }

    public void setOnActionCompletion(String onActionCompletion) {
        this.onActionCompletion = onActionCompletion;
    }
}
