package org.avni.server.application;

import org.avni.server.domain.Concept;

public class TestFormElementBuilder {
    private final FormElement formElement = new FormElement();

    public TestFormElementBuilder() {
        formElement.setKeyValues(new KeyValues());
    }

    public TestFormElementBuilder withUuid(String uuid) {
        formElement.setUuid(uuid);
        return this;
    }

    public TestFormElementBuilder withName(String name) {
        formElement.setName(name);
    	return this;
    }

    public TestFormElementBuilder withId(long id) {
        formElement.setId(id);
    	return this;
    }

    public TestFormElementBuilder withConcept(Concept concept) {
        formElement.setConcept(concept);
    	return this;
    }

    public TestFormElementBuilder withQuestionGroupElement(FormElement formElement) {
        this.formElement.setGroup(formElement);
        return this;
    }

    public TestFormElementBuilder withRepeatable(boolean isRepeatable) {
        formElement.getKeyValues().add(new KeyValue(KeyType.repeatable, isRepeatable));
    	return this;
    }

    public TestFormElementBuilder withReadOnly(boolean isEditable) {
        formElement.getKeyValues().add(new KeyValue(KeyType.editable, isEditable));
        return this;
    }

    public TestFormElementBuilder withType(FormElementType type) {
        formElement.setType(type.name());
    	return this;
    }

    public TestFormElementBuilder withDisplayOrder(Double displayOrder) {
        formElement.setDisplayOrder(displayOrder);
        return this;
    }

    public TestFormElementBuilder withIsVoided(boolean isVoided) {
        formElement.setVoided(isVoided);
        return this;
    }

    public TestFormElementBuilder withRule(String rule) {
        formElement.setRule(rule);
        return this;
    }

    public TestFormElementBuilder withMandatory(boolean isMandatory) {
        formElement.setMandatory(isMandatory);
        return this;
    }

    public TestFormElementBuilder withType(String type) {
        formElement.setType(type);
        return this;
    }

    public TestFormElementBuilder withFormElementGroup(FormElementGroup formElementGroup) {
        formElement.setFormElementGroup(formElementGroup);
        formElementGroup.addFormElement(formElement);
        return this;
    }

    public FormElement build() {
        return formElement;
    }
}
