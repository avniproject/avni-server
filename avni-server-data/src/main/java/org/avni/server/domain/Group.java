package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "groups")
@BatchSize(size = 100)
public class Group extends OrganisationAwareEntity {
    public static final String Administrators = "Administrators";
    public static final String Everyone = "Everyone";
    public static final String METABASE_USERS = "Metabase Users";
    ;

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

    public boolean isAdministrator() {
        return Administrators.equals(name);
    }

    public boolean isEveryone() {
        return Everyone.equals(name);
    }

    public boolean isOneOfTheDefaultGroups() {
        return Administrators.equals(name) || Everyone.equals(name) || METABASE_USERS.equals(name);
    }
}
