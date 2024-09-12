package org.avni.server.domain.metabase;

public enum ConditionType {
    EQUAL("="),
    NOT_EQUAL("!=");

    private final String operator;

    ConditionType(String operator) {
        this.operator = operator;
    }

    public String getOperator() {
        return operator;
    }
}
