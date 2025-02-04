package org.avni.ruleServer.domain;

public class RuleInput {
    private Imports imports;
    private RuleParams params;

    public Imports getImports() {
        return imports;
    }

    public void setImports(Imports imports) {
        this.imports = imports;
    }

    public RuleParams getParams() {
        return params;
    }

    public void setParams(RuleParams params) {
        this.params = params;
    }
}
