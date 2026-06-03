package org.avni.server.domain.attendance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.domain.SubjectType;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "attendance_type")
@BatchSize(size = 100)
@JsonIgnoreProperties({"subjectType"})
public class AttendanceType extends OrganisationAwareEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_type_id")
    private SubjectType subjectType;

    @NotNull
    @Column
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column
    @Type(value = JSONObjectUserType.class)
    private JsonObject config;

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public String getSubjectTypeUUID() {
        return subjectType == null ? null : subjectType.getUuid();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public JsonObject getConfig() {
        return config;
    }

    public void setConfig(JsonObject config) {
        this.config = config;
    }
}
