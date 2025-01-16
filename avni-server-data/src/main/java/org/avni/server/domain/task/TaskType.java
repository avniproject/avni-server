package org.avni.server.domain.task;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.framework.hibernate.ArrayUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "task_type")
@BatchSize(size = 100)
public class TaskType extends OrganisationAwareEntity {
    @Column
    @NotNull
    private String name;

    @Column
    @Enumerated(EnumType.STRING)
    @NotNull
    private TaskTypeName type;

    @Column(columnDefinition = "text[]")
    @Type(value = ArrayUserType.class)
    private String[] metadataSearchFields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskTypeName getType() {
        return type;
    }

    public void setType(TaskTypeName type) {
        this.type = type;
    }

    public String[] getMetadataSearchFields() {
        return metadataSearchFields;
    }

    public void setMetadataSearchFields(String[] metadataSearchFields) {
        this.metadataSearchFields = metadataSearchFields;
    }
}
