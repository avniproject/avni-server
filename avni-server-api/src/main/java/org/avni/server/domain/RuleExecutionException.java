package org.avni.server.domain;

import org.avni.server.web.request.rules.response.RuleError;

public class RuleExecutionException extends Exception {
    private final RuleError ruleError;

    public RuleExecutionException(RuleError ruleError) {
        this.ruleError = ruleError;
    }

    public RuleError getRuleError() {
        return ruleError;
    }
}
