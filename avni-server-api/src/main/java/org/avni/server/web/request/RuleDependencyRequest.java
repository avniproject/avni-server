package org.avni.server.web.request;

import org.avni.server.domain.RuleDependency;

public class RuleDependencyRequest {
    private String code;
    private String hash;

    public static RuleDependencyRequest fromRuleDependency(RuleDependency ruleDependency) {
        RuleDependencyRequest request = new RuleDependencyRequest();
        request.setCode(ruleDependency.getCode());
        request.setHash(ruleDependency.getChecksum());
        return request;
    }
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
