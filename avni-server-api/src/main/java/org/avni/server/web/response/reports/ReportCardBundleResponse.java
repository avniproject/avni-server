package org.avni.server.web.response.reports;

import org.avni.server.web.contract.ReportCardContract;

import java.util.ArrayList;
import java.util.List;

public class ReportCardBundleResponse extends ReportCardContract {
    private String standardReportCardType;
    private List<String> standardReportCardInputSubjectTypes = new ArrayList<>();
    private List<String> standardReportCardInputPrograms = new ArrayList<>();
    private List<String> standardReportCardInputEncounterTypes = new ArrayList<>();

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
}
