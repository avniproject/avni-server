package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "groups")
@BatchSize(size = 100)
public class Group extends OrganisationAwareEntity {

    @Column
    private String name;

    @Column
    private boolean hasAllPrivileges;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "group")
    private Set<GroupPrivilege> groupPrivileges = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isHasAllPrivileges() {
        return hasAllPrivileges;
    }

    public void setHasAllPrivileges(boolean hasAllPrivileges) {
        this.hasAllPrivileges = hasAllPrivileges;
    }

    @JsonIgnore
    public Set<GroupPrivilege> getGroupPrivileges() {
        return groupPrivileges;
    }
}
