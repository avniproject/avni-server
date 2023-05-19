package org.avni.server.common.builder;

import org.avni.server.web.request.application.FormElementContract;
import org.avni.server.web.request.application.FormElementGroupContract;

public class TestFormElementGroupContractBuilder {
    private final FormElementGroupContract formElementGroup;

    public TestFormElementGroupContractBuilder() {
        formElementGroup = new FormElementGroupContract();
    }

    public TestFormElementGroupContractBuilder withName(String name) {
        formElementGroup.setName(name);
        return this;
    }

    public TestFormElementGroupContractBuilder withUUID(String uuid) {
        formElementGroup.setUuid(uuid);
        return this;
    }

    public TestFormElementGroupContractBuilder atOrder(Integer order) {
        formElementGroup.setDisplayOrder(Double.valueOf(String.valueOf(order)));
        return this;
    }

    public TestFormElementGroupContractBuilder addFormElement(FormElementContract formElementContract) {
        this.formElementGroup.addFormElement(formElementContract);
        return this;
    }

    public FormElementGroupContract build() {
        return this.formElementGroup;
    }
}
