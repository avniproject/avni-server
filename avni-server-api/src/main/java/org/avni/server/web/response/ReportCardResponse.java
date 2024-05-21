package org.avni.server.web.response;

import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.avni.server.web.request.SubjectTypeContract;

import java.util.ArrayList;
import java.util.List;

public class ReportCardResponse extends ReportCardContract {
    private StandardReportCardTypeContract standardReportCardType;

    private List<SubjectTypeContract> standardReportCardInputSubjectTypes = new ArrayList<>();
    private List<ProgramContract> standardReportCardInputPrograms = new ArrayList<>();
    private List<EncounterTypeContract> standardReportCardInputEncounterTypes = new ArrayList<>();

    public List<SubjectTypeContract> getStandardReportCardInputSubjectTypes() {
        return standardReportCardInputSubjectTypes;
    }

    public void setStandardReportCardInputSubjectTypes(List<SubjectTypeContract> standardReportCardInputSubjectTypes) {
        this.standardReportCardInputSubjectTypes = standardReportCardInputSubjectTypes;
    }

    public List<ProgramContract> getStandardReportCardInputPrograms() {
        return standardReportCardInputPrograms;
    }

    public void setStandardReportCardInputPrograms(List<ProgramContract> standardReportCardInputPrograms) {
        this.standardReportCardInputPrograms = standardReportCardInputPrograms;
    }

    public List<EncounterTypeContract> getStandardReportCardInputEncounterTypes() {
        return standardReportCardInputEncounterTypes;
    }

    public void setStandardReportCardInputEncounterTypes(List<EncounterTypeContract> standardReportCardInputEncounterTypes) {
        this.standardReportCardInputEncounterTypes = standardReportCardInputEncounterTypes;
    }

    public StandardReportCardTypeContract getStandardReportCardType() {
        return standardReportCardType;
    }

    public void setStandardReportCardType(StandardReportCardTypeContract standardReportCardType) {
        this.standardReportCardType = standardReportCardType;
    }
}
