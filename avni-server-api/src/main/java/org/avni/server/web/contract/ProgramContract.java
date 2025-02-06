package org.avni.server.web.contract;

import org.avni.server.domain.DeclarativeRule;
import org.avni.server.domain.Program;

public class ProgramContract {
    private String name;
    private String uuid;
    private String colour;
    private boolean voided;
    private Boolean active;
    private String enrolmentEligibilityCheckRule;
    private String enrolmentSummaryRule;
    private DeclarativeRule enrolmentEligibilityCheckDeclarativeRule;
    private boolean manualEligibilityCheckRequired;
    private boolean showGrowthChart;
    private String manualEnrolmentEligibilityCheckRule;
    private DeclarativeRule manualEnrolmentEligibilityCheckDeclarativeRule;
    private boolean allowMultipleEnrolments;

    public boolean isManualEligibilityCheckRequired() {
        return manualEligibilityCheckRequired;
    }

    public void setManualEligibilityCheckRequired(boolean manualEligibilityCheckRequired) {
        this.manualEligibilityCheckRequired = manualEligibilityCheckRequired;
    }

    public boolean isShowGrowthChart() {
        return showGrowthChart;
    }

    public void setShowGrowthChart(boolean showGrowthChart) {
        this.showGrowthChart = showGrowthChart;
    }

    public String getManualEnrolmentEligibilityCheckRule() {
        return manualEnrolmentEligibilityCheckRule;
    }

    public void setManualEnrolmentEligibilityCheckRule(String manualEnrolmentEligibilityCheckRule) {
        this.manualEnrolmentEligibilityCheckRule = manualEnrolmentEligibilityCheckRule;
    }

    public DeclarativeRule getManualEnrolmentEligibilityCheckDeclarativeRule() {
        return manualEnrolmentEligibilityCheckDeclarativeRule;
    }

    public void setManualEnrolmentEligibilityCheckDeclarativeRule(DeclarativeRule manualEnrolmentEligibilityCheckDeclarativeRule) {
        this.manualEnrolmentEligibilityCheckDeclarativeRule = manualEnrolmentEligibilityCheckDeclarativeRule;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public boolean isVoided() {
        return voided;
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }

    public String getEnrolmentEligibilityCheckRule() {
        return enrolmentEligibilityCheckRule;
    }

    public void setEnrolmentEligibilityCheckRule(String enrolmentEligibilityCheckRule) {
        this.enrolmentEligibilityCheckRule = enrolmentEligibilityCheckRule;
    }

    public String getEnrolmentSummaryRule() {
        return enrolmentSummaryRule;
    }

    public void setEnrolmentSummaryRule(String enrolmentSummaryRule) {
        this.enrolmentSummaryRule = enrolmentSummaryRule;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public DeclarativeRule getEnrolmentEligibilityCheckDeclarativeRule() {
        return enrolmentEligibilityCheckDeclarativeRule;
    }

    public void setEnrolmentEligibilityCheckDeclarativeRule(DeclarativeRule enrolmentEligibilityCheckDeclarativeRule) {
        this.enrolmentEligibilityCheckDeclarativeRule = enrolmentEligibilityCheckDeclarativeRule;
    }

    public boolean isAllowMultipleEnrolments() {
        return allowMultipleEnrolments;
    }

    public void setAllowMultipleEnrolments(boolean allowMultipleEnrolments) {
        this.allowMultipleEnrolments = allowMultipleEnrolments;
    }

    public static ProgramContract createBasic(Program program) {
        ProgramContract contract = new ProgramContract();
        contract.setName(program.getName());
        contract.setUuid(program.getUuid());
        return contract;
    }

    @Override
    public String toString() {
        return "{" +
                "uuid='" + uuid + '\'' +
                '}';
    }
}
