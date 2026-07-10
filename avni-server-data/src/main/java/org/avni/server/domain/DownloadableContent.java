package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

// The AES key for an encrypted blob is never stored on this entity - it lives in the server-only ModelKey store.
@Entity
@Table(name = "downloadable_content")
@BatchSize(size = 100)
public class DownloadableContent extends OrganisationAwareEntity {

    @NotNull
    @Column
    private String name;

    @NotNull
    @Column
    private String category;

    @Column(name = "content_key")
    private String contentKey;

    @Column
    private String sha256;

    @Column(name = "needs_key")
    private boolean needsKey;

    @Column
    @Type(value = JSONObjectUserType.class)
    private JsonObject payload;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContentKey() {
        return contentKey;
    }

    public void setContentKey(String contentKey) {
        this.contentKey = contentKey;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public boolean isNeedsKey() {
        return needsKey;
    }

    public void setNeedsKey(boolean needsKey) {
        this.needsKey = needsKey;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }
}
