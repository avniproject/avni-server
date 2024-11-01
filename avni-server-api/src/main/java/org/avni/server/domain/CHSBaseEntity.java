package org.avni.server.domain;

import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import jakarta.persistence.*;
import java.util.UUID;

@MappedSuperclass
public class CHSBaseEntity {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    @Id
    private Long id;

    @Column
    @NotNull
    private String uuid;

    @Column
    private boolean isVoided;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isVoided() {
        return isVoided;
    }

    public void setVoided(boolean voided) {
        isVoided = voided;
    }

    public void setVoided(Boolean voided) {
        isVoided = voided;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void assignUUID() {
        this.uuid = UUID.randomUUID().toString();
    }

    public void assignUUIDIfRequired() {
        if (this.uuid == null) this.assignUUID();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        LazyInitializer lazyInitializer1 = HibernateProxy.extractLazyInitializer(this);
        LazyInitializer lazyInitializer2 = HibernateProxy.extractLazyInitializer(o);
        if (o == null || lazyInitializer2 == null || lazyInitializer1 == null || lazyInitializer1.getImplementationClass() != lazyInitializer2.getImplementationClass()) return false;

        CHSBaseEntity that = (CHSBaseEntity) o;

        if (getId() != null ? !getId().equals(that.getId()) : that.getId() != null) return false;
        return getUuid() != null ? getUuid().equals(that.getUuid()) : that.getUuid() == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }
}
