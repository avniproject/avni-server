package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "organisation_group")
@BatchSize(size = 100)
public class OrganisationGroup extends ETLEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column
    @NotNull
    private String uuid;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void assignUUIDIfRequired() {
        if (this.uuid == null)
            this.uuid = UUID.randomUUID().toString();
    }
}
