package org.avni.server.web.response.reports;

import org.avni.server.web.contract.ReportCardContract;

import java.util.ArrayList;
import java.util.List;

public class ReportCardBundleContract extends ReportCardContract {
    private String standardReportCardType;
    private List<String> standardReportCardInputSubjectTypes = new ArrayList<>();
    private List<String> standardReportCardInputPrograms = new ArrayList<>();
    private List<String> standardReportCardInputEncounterTypes = new ArrayList<>();
    private String standardReportCardInputRecentDuration = null;
    private String action;
    private String actionEncounterTypeUUID;
    private String onActionCompletion;

    public String getStandardReportCardType() {
        return standardReportCardType;
    }

    public void setStandardReportCardType(String standardReportCardType) {
        this.standardReportCardType = standardReportCardType;
    }

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

    public String getStandardReportCardInputRecentDuration() {
        return standardReportCardInputRecentDuration;
    }

    public void setStandardReportCardInputRecentDuration(String standardReportCardInputRecentDuration) {
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
