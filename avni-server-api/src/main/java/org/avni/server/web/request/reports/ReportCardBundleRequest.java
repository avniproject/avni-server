package org.avni.server.web.request.reports;

import org.avni.server.web.contract.ReportCardContract;

import java.util.List;

public class ReportCardBundleRequest extends ReportCardContract {
    private String standardReportCardType;
    private List<String> standardReportCardInputSubjectTypes;
    private List<String> standardReportCardInputPrograms;
    private List<String> standardReportCardInputEncounterTypes;
    private String standardReportCardInputRecentDuration;

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
}
