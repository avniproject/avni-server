package org.avni.server.web.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.tika.utils.StringUtils;
import org.avni.server.domain.TemplateOrganisation;
import org.avni.server.web.validation.ValidationException;

public class TemplateOrganisationContract extends ReferenceDataContract {
    private String description;
    private String summary;
    private Long organisationId;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private boolean active = true;

    public static TemplateOrganisationContract fromEntity(TemplateOrganisation templateOrganisation) {
        TemplateOrganisationContract contract = new TemplateOrganisationContract();
        contract.setId(templateOrganisation.getId());
        contract.setUuid(templateOrganisation.getUuid());
        contract.setName(templateOrganisation.getName());
        contract.setDescription(templateOrganisation.getDescription());
        contract.setSummary(templateOrganisation.getSummary());
        contract.setOrganisationId(templateOrganisation.getOrganisation().getId());
        contract.setActive(templateOrganisation.isActive());
        return contract;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void validate() throws ValidationException {
        if (StringUtils.isBlank(this.getName())) {
            throw new ValidationException("Name is required");
        }
        if (this.getOrganisationId() == null) {
            throw new ValidationException("Organisation ID is required");
        }
    }
}
