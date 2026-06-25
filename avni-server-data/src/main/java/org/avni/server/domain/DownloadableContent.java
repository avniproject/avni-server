package org.avni.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.avni.server.framework.hibernate.JSONObjectUserType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

/**
 * Generic admin-configured reference data that declares a downloadable blob (e.g. an edge model).
 * Synced to every device as metadata (admin-managed, global - like forms/concepts/extensions).
 * The bytes referenced by {@code contentKey} are fetched during sync by the client.
 * <p>
 * The AES key for an encrypted blob is NEVER stored on this entity - it lives in a separate
 * server-only key store served via a device key-delivery endpoint (avniproject/avni-server#1020).
 */
@Entity
@Table(name = "downloadable_content")
@BatchSize(size = 100)
public class DownloadableContent extends OrganisationAwareEntity {

    @NotNull
    @Column
    private String name;

    /**
     * Content category, e.g. {@code edgeModel}. Drives category-specific interpretation of {@link #payload}.
     */
    @NotNull
    @Column
    private String category;

    /**
     * Object key of the downloadable blob, e.g. {@code models/<sha256OfPlaintext>.bin}.
     * The {@code models/} prefix is the data-class signal for storage routing
     * (avniproject/avni-server#1013); a non-null contentKey + sha256 is the "has downloadable
     * content" signal the client uses to drive the content-download capability.
     */
    @Column(name = "content_key")
    private String contentKey;

    /**
     * SHA-256 of the plaintext blob. Cache key and key-store lookup key on the client.
     */
    @Column
    private String sha256;

    /**
     * Whether the blob is encrypted and the client must fetch an AES key from the key store
     * (avniproject/avni-server#1020) to use it. The key itself is never a field here.
     */
    @Column(name = "needs_key")
    private boolean needsKey;

    /**
     * Category-specific, non-secret metadata (for the edge model: engine / inputShape / labelMap).
     */
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
