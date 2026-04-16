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
    private String actionDetailSubjectTypeUUID;
    private String actionDetailProgramUUID;
    private String actionDetailEncounterTypeUUID;
    private String actionDetailVisitType;
    private String customCardConfigUUID;

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

    public String getActionDetailSubjectTypeUUID() {
        return actionDetailSubjectTypeUUID;
    }

    public void setActionDetailSubjectTypeUUID(String actionDetailSubjectTypeUUID) {
        this.actionDetailSubjectTypeUUID = actionDetailSubjectTypeUUID;
    }

    public String getActionDetailProgramUUID() {
        return actionDetailProgramUUID;
    }

    public void setActionDetailProgramUUID(String actionDetailProgramUUID) {
        this.actionDetailProgramUUID = actionDetailProgramUUID;
    }

    public String getActionDetailEncounterTypeUUID() {
        return actionDetailEncounterTypeUUID;
    }

    public void setActionDetailEncounterTypeUUID(String actionDetailEncounterTypeUUID) {
        this.actionDetailEncounterTypeUUID = actionDetailEncounterTypeUUID;
    }

    public String getActionDetailVisitType() {
        return actionDetailVisitType;
    }

    public void setActionDetailVisitType(String actionDetailVisitType) {
        this.actionDetailVisitType = actionDetailVisitType;
    }

    public String getCustomCardConfigUUID() {
        return customCardConfigUUID;
    }

    public void setCustomCardConfigUUID(String customCardConfigUUID) {
        this.customCardConfigUUID = customCardConfigUUID;
    }
}
