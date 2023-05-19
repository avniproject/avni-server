package org.avni.server.application;

import java.util.Arrays;

public class TestFormBuilder {
    private final Form form = new Form();

    public TestFormBuilder addFormElementGroup(FormElementGroup ... formElementGroups ) {
        Arrays.stream(formElementGroups).forEach(form::addFormElementGroup);
    	return this;
    }

    public Form build() {
        return form;
    }
}
