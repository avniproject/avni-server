package org.avni.server.web.request;

import org.avni.server.web.contract.ReportCardContract;

import java.util.List;

public class ReportCardRequest extends ReportCardContract {
    private Long standardReportCardTypeId;
    private List<String> standardReportCardInputSubjectTypes;
    private List<String> standardReportCardInputPrograms;
    private List<String> standardReportCardInputEncounterTypes;

    public Long getStandardReportCardTypeId() {
        return standardReportCardTypeId;
    }

    public void setStandardReportCardTypeId(Long standardReportCardTypeId) {
        this.standardReportCardTypeId = standardReportCardTypeId;
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
