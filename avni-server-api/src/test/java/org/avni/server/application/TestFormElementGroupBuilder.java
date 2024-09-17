package org.avni.server.application;

import java.util.Arrays;

public class TestFormElementGroupBuilder {
    private final FormElementGroup formElementGroup = new FormElementGroup();

    public TestFormElementGroupBuilder addFormElement(FormElement ... formElements) {
        Arrays.stream(formElements).forEach(formElement -> {
            formElementGroup.addFormElement(formElement);
            formElement.setFormElementGroup(formElementGroup);
        });
    	return this;
    }

    public TestFormElementGroupBuilder withUuid(String uuid) {
        formElementGroup.setUuid(uuid);
        return this;
    }

    public TestFormElementGroupBuilder withName(String name) {
        formElementGroup.setName(name);
        return this;
    }

    public TestFormElementGroupBuilder withDisplayOrder(Double displayOrder) {
        formElementGroup.setDisplayOrder(displayOrder);
        return this;
    }

    public TestFormElementGroupBuilder withIsVoided(boolean isVoided) {
        formElementGroup.setVoided(isVoided);
        return this;
    }

    public TestFormElementGroupBuilder withDisplay(String display) {
        formElementGroup.setDisplay(display);
        return this;
    }

    public TestFormElementGroupBuilder withRule(String rule) {
        formElementGroup.setRule(rule);
        return this;
    }

    public TestFormElementGroupBuilder withTimed(boolean isTimed) {
        formElementGroup.setTimed(isTimed);
        return this;
    }

    public FormElementGroup build() {
        return formElementGroup;
    }
}
