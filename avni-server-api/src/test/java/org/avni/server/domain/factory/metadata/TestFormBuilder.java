package org.avni.server.domain.factory.metadata;

import org.avni.server.application.Form;
import org.avni.server.application.FormElementGroup;
import org.avni.server.application.FormType;

import java.util.Arrays;
import java.util.UUID;

public class TestFormBuilder {
    private final Form entity = new Form();

    public TestFormBuilder withUuid(String uuid) {
        entity.setUuid(uuid);
        return this;
    }

    public TestFormBuilder withName(String name) {
        entity.setName(name);
    	return this;
    }

    public TestFormBuilder withFormType(FormType formType) {
        entity.setFormType(formType);
    	return this;
    }

    public TestFormBuilder withDefaultFieldsForNewEntity() {
        String s = UUID.randomUUID().toString();
        return withUuid(s).withName(s).withFormType(FormType.IndividualProfile);
    }

    public TestFormBuilder addFormElementGroup(FormElementGroup... formElementGroups ) {
        Arrays.stream(formElementGroups).forEach(formElementGroup -> {
            entity.addFormElementGroup(formElementGroup);
            formElementGroup.setForm(entity);
        });
        return this;
    }

    public Form build() {
        return entity;
    }
}
