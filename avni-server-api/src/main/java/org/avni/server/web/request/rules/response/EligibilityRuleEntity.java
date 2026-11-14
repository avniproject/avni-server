package org.avni.server.web.request.rules.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigInteger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EligibilityRuleEntity {
    @JsonProperty(value = "isEligible", access = JsonProperty.Access.READ_ONLY)
    private boolean isEligible;
    private String typeUUID;
    private RuleError error;
    private String invalidRuleResult;

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
        if (error != null || invalidRuleResult == null) return error;
        String message = String.format("Eligibility rule returned %s; it must return true or false", invalidRuleResult);
        RuleError invalidRuleResultError = new RuleError();
        invalidRuleResultError.setMessage(message);
        invalidRuleResultError.setStack(message);
        return invalidRuleResultError;
    }

    public void setError(RuleError error) {
        this.error = error;
    }

    public boolean hasFailed() {
        return error != null || invalidRuleResult != null;
    }

    @JsonSetter("isEligible")
    public void setEligibleFromRuleResult(Object ruleResult) {
        isEligible = false;
        invalidRuleResult = null;
        if (ruleResult == null) return;
        if (ruleResult instanceof Boolean) {
            isEligible = (Boolean) ruleResult;
        } else if (ruleResult instanceof Integer || ruleResult instanceof Long || ruleResult instanceof BigInteger) {
            isEligible = ((Number) ruleResult).longValue() != 0;
        } else if (ruleResult instanceof String && isTrueFalseOrEmpty(((String) ruleResult).trim())) {
            isEligible = Boolean.parseBoolean(((String) ruleResult).trim());
        } else {
            invalidRuleResult = ruleResult instanceof String ? "\"" + ruleResult + "\"" : String.valueOf(ruleResult);
        }
    }

    private static boolean isTrueFalseOrEmpty(String text) {
        return text.isEmpty() || text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false");
    }
}
