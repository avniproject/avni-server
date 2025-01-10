package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

import java.util.Set;

@Entity(name = "rule_dependency")
@BatchSize(size = 100)
public class RuleDependency extends OrganisationAwareEntity {
    public static String GLOBAL_RULE_UUID = "8ae72815-5670-40a4-b8b6-e457d0dff8ad";

    @NotNull
    @Column(name = "checksum")
    private String checksum;

    @NotNull
    @Column(name = "code")
    private String code;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "ruleDependency")
    private Set<Rule> rules;

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Set<Rule> getRules() {
        return rules;
    }

    public void setRules(Set<Rule> rules) {
        this.rules = rules;
    }
}
