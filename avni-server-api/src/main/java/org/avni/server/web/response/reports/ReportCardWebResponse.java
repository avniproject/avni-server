package org.avni.server.web.response.reports;

import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.domain.ValueUnit;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.avni.server.web.request.SubjectTypeContract;

import java.util.ArrayList;
import java.util.List;

public class ReportCardWebResponse extends ReportCardContract {
    private StandardReportCardTypeContract standardReportCardType;

    private List<SubjectTypeContract> standardReportCardInputSubjectTypes = new ArrayList<>();
    private List<ProgramContract> standardReportCardInputPrograms = new ArrayList<>();
    private List<EncounterTypeContract> standardReportCardInputEncounterTypes = new ArrayList<>();
    private ValueUnit standardReportCardInputRecentDuration = null;
    private String action;
    private String actionDetailSubjectTypeUUID;
    private String actionDetailProgramUUID;
    private String actionDetailEncounterTypeUUID;
    private String actionDetailVisitType;
    private String customCardConfigUUID;

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
