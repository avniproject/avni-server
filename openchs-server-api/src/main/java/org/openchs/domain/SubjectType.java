package org.openchs.domain;

import org.hibernate.annotations.Type;
import org.openchs.application.CoreFields;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "subject_type")
public class SubjectType extends OrganisationAwareEntity {
    @NotNull
    @Column
    private String name;

    @Column
    @Type(type = "coreFields")
    private CoreFields coreFields;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public CoreFields getCoreFields() {
        return coreFields;
    }

    public void setCoreFields(CoreFields coreFields) {
        this.coreFields = coreFields;
    }
}
