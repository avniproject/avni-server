package org.avni.server.web.request;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.organisation.OrganisationCategory;

public class OrganisationContract extends ETLContract {
    private Long parentOrganisationId;
    private String mediaDirectory;
    private String usernameSuffix;
    private Long accountId;
    private OrganisationCategory category;
    private String region;

    public static OrganisationContract fromEntity(Organisation organisation) {
        OrganisationContract organisationContract = new OrganisationContract();
        organisationContract.setId(organisation.getId());
        organisationContract.setUuid(organisation.getUuid());
        organisationContract.setParentOrganisationId(organisation.getParentOrganisationId());
        organisationContract.setMediaDirectory(organisation.getMediaDirectory());
        organisationContract.setUsernameSuffix(organisation.getEffectiveUsernameSuffix());
        organisationContract.setAccountId(organisation.getAccount() == null ? null : organisation.getAccount().getId());
        organisationContract.setCategory(organisation.getCategory());
        organisationContract.region = organisation.getAccount().getRegion();
        mapEntity(organisationContract, organisation);
        return organisationContract;
    }

    public Long getParentOrganisationId() {
        return parentOrganisationId;
    }

    public void setParentOrganisationId(Long parentOrganisationId) {
        this.parentOrganisationId = parentOrganisationId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getUsernameSuffix() {
        return usernameSuffix;
    }

    public void setUsernameSuffix(String usernameSuffix) {
        this.usernameSuffix = usernameSuffix;
    }

    public String getMediaDirectory() {
        return mediaDirectory;
    }

    public void setMediaDirectory(String mediaDirectory) {
        this.mediaDirectory = mediaDirectory;
    }

    public OrganisationCategory getCategory() {
        return category;
    }

    public void setCategory(OrganisationCategory category) {
        this.category = category;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
