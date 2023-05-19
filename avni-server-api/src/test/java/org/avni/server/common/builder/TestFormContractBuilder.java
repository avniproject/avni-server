package org.avni.server.common.builder;

import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.application.FormElementGroupContract;

public class TestFormContractBuilder {

    private final FormContract form;

    public TestFormContractBuilder() {
        form = new FormContract();
    }

    public TestFormContractBuilder withName(String name) {
        form.setName(name);
        return this;
    }

    public TestFormContractBuilder withUUID(String uuid) {
        form.setUuid(uuid);
        return this;
    }

    public TestFormContractBuilder ofFormType(String formType) {
        form.setFormType(formType);
        return this;
    }

    public TestFormContractBuilder addFormElementGroup(FormElementGroupContract formElementGroup) {
        form.addFormElementGroup(formElementGroup);
        return this;
    }

    public FormContract build() {
        return this.form;
    }
}
