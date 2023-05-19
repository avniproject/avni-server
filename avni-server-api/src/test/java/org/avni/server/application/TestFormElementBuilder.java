package org.avni.server.application;

import org.avni.server.domain.Concept;

public class TestFormElementBuilder {
    private final FormElement formElement = new FormElement();

    public TestFormElementBuilder() {
        formElement.setKeyValues(new KeyValues());
    }

    public TestFormElementBuilder withId(long id) {
        formElement.setId(id);
    	return this;
    }

    public TestFormElementBuilder withConcept(Concept concept) {
        formElement.setConcept(concept);
    	return this;
    }

    public TestFormElementBuilder withGroup(FormElement groupFormElement) {
        formElement.setGroup(groupFormElement);
    	return this;
    }

    public TestFormElementBuilder withRepeatable(boolean isRepeatable) {
        formElement.getKeyValues().add(new KeyValue(KeyType.repeatable, isRepeatable));
    	return this;
    }

    public FormElement build() {
        return formElement;
    }
}
