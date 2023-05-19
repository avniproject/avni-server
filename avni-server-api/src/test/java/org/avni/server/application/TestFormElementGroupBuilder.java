package org.avni.server.application;

import java.util.Arrays;

public class TestFormElementGroupBuilder {
    private final FormElementGroup formElementGroup = new FormElementGroup();

    public TestFormElementGroupBuilder addFormElement(FormElement ... formElements) {
        Arrays.stream(formElements).forEach(formElementGroup::addFormElement);
    	return this;
    }

    public FormElementGroup build() {
        return formElementGroup;
    }
}
