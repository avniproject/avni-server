package org.avni.server.importer.batch.csv.contract;

import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decisions;
import org.avni.server.web.request.rules.RulesContractWrapper.VisitSchedule;

import java.util.ArrayList;
import java.util.List;

public class UploadRuleServerResponseContract {
    private List<ObservationRequest> observations;
    private List<String> errors;
    private Decisions decisions;
    private List<VisitSchedule> visitSchedules;

    public static UploadRuleServerResponseContract nullObject() {
        UploadRuleServerResponseContract uploadRuleServerResponseContract = new UploadRuleServerResponseContract();
        uploadRuleServerResponseContract.errors = new ArrayList<>();
        uploadRuleServerResponseContract.decisions = Decisions.nullObject();
        uploadRuleServerResponseContract.visitSchedules = new ArrayList<>();
        uploadRuleServerResponseContract.observations = new ArrayList<>();
        return uploadRuleServerResponseContract;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public Decisions getDecisions() {
        return decisions;
    }

    public void setDecisions(Decisions decisions) {
        this.decisions = decisions;
    }

    public List<VisitSchedule> getVisitSchedules() {
        return visitSchedules;
    }

    public void setVisitSchedules(List<VisitSchedule> visitSchedules) {
        this.visitSchedules = visitSchedules;
    }

    public List<ObservationRequest> getObservations() {
        return observations;
    }

    public void setObservations(List<ObservationRequest> observations) {
        this.observations = observations;
    }
}
