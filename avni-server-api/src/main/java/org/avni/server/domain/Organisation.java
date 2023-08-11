package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Entity
@Table(name = "organisation")
@BatchSize(size = 100)
public class Organisation extends ETLEntity {
    @Column
    private String mediaDirectory;

    @Column
    private Long parentOrganisationId;

    @Column
    private String usernameSuffix;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    public Organisation() {
    }

    public Organisation(String name) {
        this.setName(name);
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getMediaDirectory() {
        return mediaDirectory;
    }

    public void setMediaDirectory(String mediaDirectory) {
        this.mediaDirectory = mediaDirectory;
    }

    public Long getParentOrganisationId() {
        return parentOrganisationId;
    }

    public void setParentOrganisationId(Long parentOrganisationId) {
        this.parentOrganisationId = parentOrganisationId;
    }

    /**
     * Use getEffectiveUsernameSuffix instead
     */
    @Deprecated
    public String getUsernameSuffix() {
        return usernameSuffix;
    }

    public void setUsernameSuffix(String usernameSuffix) {
        this.usernameSuffix = usernameSuffix;
    }

    @JsonIgnore
    public boolean isNew() {
        Long id = getId();
        return (id == null || id == 0);
    }

    public String getEffectiveUsernameSuffix() {
        return usernameSuffix == null ? getDbUser() : usernameSuffix;
    }
}
