package org.avni.server.domain.factory.metadata;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.domain.Program;

public class FormMappingBuilder {
    private final FormMapping formMapping = new FormMapping();

    public FormMappingBuilder withProgram(Program program) {
        formMapping.setProgram(program);
        return this;
    }

    public FormMappingBuilder withForm(Form form) {
        formMapping.setForm(form);
    	return this;
    }

    public FormMapping build() {
        return formMapping;
    }
}
