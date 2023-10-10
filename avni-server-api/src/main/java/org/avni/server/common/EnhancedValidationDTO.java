package org.avni.server.common;

import org.avni.server.application.FormElement;
import org.avni.server.domain.Concept;

public class EnhancedValidationDTO {
    private Concept concept;
    private FormElement formElement;
    private Object value;

    public EnhancedValidationDTO(Concept concept, FormElement formElement, Object value) {
        this.concept = concept;
        this.formElement = formElement;
        this.value = value;
    }

    public Concept getConcept() {
        return concept;
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
    }

    public FormElement getFormElement() {
        return formElement;
    }

    public void setFormElement(FormElement formElement) {
        this.formElement = formElement;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
