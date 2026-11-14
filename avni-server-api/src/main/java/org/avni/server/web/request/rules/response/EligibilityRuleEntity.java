package org.avni.server.web.request.rules.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EligibilityRuleEntity {
    @JsonProperty("isEligible")
    private boolean isEligible;
    private String typeUUID;
    private RuleError error;

    public EligibilityRuleEntity() {
    }

    public EligibilityRuleEntity(boolean isEligible, String typeUUID) {
        this.isEligible = isEligible;
        this.typeUUID = typeUUID;
    }

    public String getTypeUUID() {
        return typeUUID;
    }

    public void setTypeUUID(String typeUUID) {
        this.typeUUID = typeUUID;
    }

    public boolean isEligible() {
        return isEligible;
    }

    public void setEligible(boolean eligible) {
        isEligible = eligible;
    }

    public RuleError getError() {
        return error;
    }

    public void setError(RuleError error) {
        this.error = error;
    }

    public boolean hasFailed() {
        return error != null;
    }

    @JsonSetter("isEligible")
    public void setEligibleFromRuleResult(Object ruleResult) {
        if (ruleResult == null) {
            isEligible = false;
        } else if (ruleResult instanceof Boolean) {
            isEligible = (Boolean) ruleResult;
        } else if (ruleResult instanceof Number) {
            isEligible = ((Number) ruleResult).doubleValue() != 0;
        } else if (ruleResult instanceof String && ((String) ruleResult).isEmpty()) {
            isEligible = false;
        } else if (ruleResult instanceof String && (((String) ruleResult).equalsIgnoreCase("true") || ((String) ruleResult).equalsIgnoreCase("false"))) {
            isEligible = Boolean.parseBoolean((String) ruleResult);
        } else {
            isEligible = false;
            if (error == null) {
                String message = String.format("Eligibility rule returned %s; it must return true or false", ruleResult instanceof String ? "\"" + ruleResult + "\"" : ruleResult);
                error = new RuleError();
                error.setMessage(message);
                error.setStack(message);
            }
        }
    }
}
