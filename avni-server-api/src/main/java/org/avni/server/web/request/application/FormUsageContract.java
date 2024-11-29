package org.avni.server.web.request.application;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormElementGroup;

public class FormUsageContract {
    private String formName;
    private String formUUID;
    private Long formId;
    private String formElementGroupUUID;
    private String formElementUUID;
    private String formElementGroupName;
    private String formElementQGGroupName;
    private String formElementName;

    static public FormUsageContract fromEntity(FormElement formElement) {
        FormUsageContract formUsageContract = new FormUsageContract();
        formUsageContract.setFormElementUUID(formElement.getUuid());
        FormElementGroup formElementGroup = formElement.getFormElementGroup();
        formUsageContract.setFormElementGroupUUID(formElementGroup.getUuid());
        Form form = formElementGroup.getForm();
        formUsageContract.setFormId(form.getId());
        formUsageContract.setFormName(form.getName());
        formUsageContract.setFormUUID(form.getUuid());
        formUsageContract.setFormElementGroupName(formElementGroup.getName());
        formUsageContract.setFormElementQGGroupName(formElement.isPartOfQuestionGroup() ? formElement.getGroup().getName() : null);
        formUsageContract.setformElementName(formElement.getName());
        return formUsageContract;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getFormUUID() {
        return formUUID;
    }

    public void setFormUUID(String formUUID) {
        this.formUUID = formUUID;
    }

    public Long getFormId() {
        return formId;
    }

    public void setFormId(Long formId) {
        this.formId = formId;
    }

    public String getFormElementGroupUUID() {
        return formElementGroupUUID;
    }

    public void setFormElementGroupUUID(String formElementGroupUUID) {
        this.formElementGroupUUID = formElementGroupUUID;
    }

    public String getFormElementUUID() {
        return formElementUUID;
    }

    public void setFormElementUUID(String formElementUUID) {
        this.formElementUUID = formElementUUID;
    }

    public void setFormElementGroupName(String formElementGroupName){
        this.formElementGroupName =  formElementGroupName;
    }
    public String getFormElementGroupName(){
        return formElementGroupName ;
    }

    public void setformElementName(String formElementName){
        this.formElementName =  formElementName;
    }

    public String getformElementName(){
        return formElementName;
    }

    public String getFormElementQGGroupName() {
        return formElementQGGroupName;
    }

    public void setFormElementQGGroupName(String formElementQGGroupName) {
        this.formElementQGGroupName = formElementQGGroupName;
    }
}
