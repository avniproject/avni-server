package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "organisation_group")
@BatchSize(size = 100)
public class OrganisationGroup extends ETLEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @JsonIgnore
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "organisationGroup")
    private Set<OrganisationGroupOrganisation> organisationGroupOrganisations = new HashSet<>();

    public Set<OrganisationGroupOrganisation> getOrganisationGroupOrganisations() {
        return organisationGroupOrganisations;
    }

    public void setOrganisationGroupOrganisations(Set<OrganisationGroupOrganisation> organisationGroupOrganisations) {
        this.organisationGroupOrganisations.clear();
        if (organisationGroupOrganisations != null) {
            this.organisationGroupOrganisations.addAll(organisationGroupOrganisations);
        }
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }
}
