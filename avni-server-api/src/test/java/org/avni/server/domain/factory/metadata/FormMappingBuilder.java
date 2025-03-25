package org.avni.server.domain.factory.metadata;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;

import java.util.UUID;

public class FormMappingBuilder {
    private final FormMapping entity = new FormMapping();

    public FormMappingBuilder() {
        withUuid(UUID.randomUUID().toString());
    }

    public FormMappingBuilder withProgram(Program program) {
        entity.setProgram(program);
        return this;
    }

    public FormMappingBuilder withEncounterType(EncounterType encounterType) {
        entity.setEncounterType(encounterType);
        return this;
    }

    public FormMappingBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public FormMappingBuilder withForm(Form form) {
        entity.setForm(form);
    	return this;
    }

    public FormMappingBuilder withSubjectType(SubjectType subjectType) {
        entity.setSubjectType(subjectType);
    	return this;
    }

    public FormMapping build() {
        return entity;
    }
}
