package org.openchs.web.request.rules.RulesContractWrapper;

import org.joda.time.DateTime;
import org.openchs.domain.ProgramEnrolment;
import org.openchs.service.ObservationService;
import org.openchs.web.request.ObservationModelContract;
import org.openchs.web.request.ProgramEncountersContract;
import org.openchs.web.request.application.ChecklistDetailRequest;
import org.openchs.web.request.rules.request.RuleRequestEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProgramEnrolmentContractWrapper {
    private IndividualContractWrapper subject;
    private RuleRequestEntity rule;
    private List<VisitSchedule> visitSchedules = new ArrayList<>();
    private DateTime enrolmentDateTime;
    private DateTime programExitDateTime;
    private String uuid;
    private Boolean voided;
    private Set<ProgramEncountersContract> programEncounters = new HashSet<>();
    private List<ObservationModelContract> observations = new ArrayList<>();
    private List<ObservationModelContract> exitObservations = new ArrayList<>();
    private String operationalProgramName;
    private List<ChecklistDetailRequest> checklistDetails = new ArrayList<>();

    public List<ChecklistDetailRequest> getChecklistDetails() {
        return checklistDetails;
    }

    public void setChecklistDetails(List<ChecklistDetailRequest> checklistDetails) {
        this.checklistDetails = checklistDetails;
    }

    public String getOperationalProgramName() {
        return operationalProgramName;
    }

    public void setOperationalProgramName(String operationalProgramName) {
        this.operationalProgramName = operationalProgramName;
    }

    public DateTime getEnrolmentDateTime() {
        return enrolmentDateTime;
    }

    public void setEnrolmentDateTime(DateTime enrolmentDateTime) {
        this.enrolmentDateTime = enrolmentDateTime;
    }

    public DateTime getProgramExitDateTime() {
        return programExitDateTime;
    }

    public void setProgramExitDateTime(DateTime programExitDateTime) {
        this.programExitDateTime = programExitDateTime;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Boolean getVoided() {
        return voided;
    }

    public void setVoided(Boolean voided) {
        this.voided = voided;
    }

    public Set<ProgramEncountersContract> getProgramEncounters() {
        return programEncounters;
    }

    public void setProgramEncounters(Set<ProgramEncountersContract> programEncounters) {
        this.programEncounters = programEncounters;
    }

    public List<ObservationModelContract> getObservations() {
        return observations;
    }

    public void setObservations(List<ObservationModelContract> observations) {
        this.observations = observations;
    }

    public List<ObservationModelContract> getExitObservations() {
        return exitObservations;
    }

    public void setExitObservations(List<ObservationModelContract> exitObservations) {
        this.exitObservations = exitObservations;
    }

    public RuleRequestEntity getRule() {
        return rule;
    }

    public void setRule(RuleRequestEntity rule) {
        this.rule = rule;
    }

    public IndividualContractWrapper getSubject() {
        return subject;
    }

    public void setSubject(IndividualContractWrapper subject) {
        this.subject = subject;
    }

    public List<VisitSchedule> getVisitSchedules() {
        return visitSchedules;
    }

    public void setVisitSchedules(List<VisitSchedule> visitSchedules) {
        this.visitSchedules = visitSchedules;
    }

    public static ProgramEnrolmentContractWrapper fromEnrolment(ProgramEnrolment enrolment, ObservationService observationService) {
        ProgramEnrolmentContractWrapper contract = new ProgramEnrolmentContractWrapper();
        contract.setUuid(enrolment.getUuid());
        contract.setEnrolmentDateTime(enrolment.getEnrolmentDateTime());
        contract.setProgramExitDateTime(enrolment.getProgramExitDateTime());
        contract.setOperationalProgramName(enrolment.getProgram().getOperationalProgramName());
        contract.setObservations(observationService.constructObservationModelContracts(enrolment.getObservations()));
        contract.setExitObservations(observationService.constructObservationModelContracts(enrolment.getProgramExitObservations()));
        return contract;
    }
}
