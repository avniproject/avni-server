package org.avni.ruleServer.domain;

import java.util.List;

public class RuleParams {
    private Object entity;
    private List<Object> decisions;

    public Object getEntity() {
        return entity;
    }

    public void setEntity(Object entity) {
        this.entity = entity;
    }

    public List<Object> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<Object> decisions) {
        this.decisions = decisions;
    }
}
