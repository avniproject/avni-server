package org.avni.server.common.builder;

import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.application.FormElementContract;

public class TestFormElementContractBuilder {
    private final FormElementContract formElement;

    public TestFormElementContractBuilder() {
        formElement = new FormElementContract();
        formElement.setMandatory(false);
        formElement.setType("SingleSelect");
        formElement.setDisplayOrder(1.1);
        formElement.setValidFormat(null);
    }

    public TestFormElementContractBuilder withName(String name) {
        formElement.setName(name);
        return this;
    }

    public TestFormElementContractBuilder withUUID(String uuid) {
        formElement.setUuid(uuid);
        return this;
    }

    public TestFormElementContractBuilder withConcept(ConceptContract conceptContract) {
        formElement.setConcept(conceptContract);
        return this;
    }

    public TestFormElementContractBuilder isMandatory() {
        formElement.setMandatory(true);
        return this;
    }

    public FormElementContract build() {
        return this.formElement;
    }

}
