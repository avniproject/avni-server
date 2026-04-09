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
    public static final String SQLITE_MIGRATION = "SQLite Migration";
    public static final String SQLITE_MIGRATION_UUID = "e6e5e4e3-e2e1-4f00-8000-d0d1d2d3d4d5";

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
        return Administrators.equals(name) || Everyone.equals(name)
                || METABASE_USERS.equals(name) || SQLITE_MIGRATION.equals(name);
    }
}
