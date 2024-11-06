package org.avni.server.domain;

import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.framework.IdHolder;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import jakarta.persistence.*;
import java.util.UUID;

@MappedSuperclass
public class CHSBaseEntity implements IdHolder {
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
        return CHSBaseEntity.equals(this, o);
    }

    public static boolean equals(IdHolder a, Object o) {
        if (a == o) return true;

        LazyInitializer lazyInitializer2 = HibernateProxy.extractLazyInitializer(o);

        if (o == null) return false;
        if (lazyInitializer2 != null && a.getClass() != lazyInitializer2.getImplementationClass()) return false;

        IdHolder that = lazyInitializer2 == null ? (IdHolder) o : (IdHolder) lazyInitializer2.getImplementation();

        if (that.getId() == null && a.getId() == null) return that == a;

        if (a.getId() != null ? !a.getId().equals(that.getId()) : that.getId() != null) return false;
        return a.getUuid() != null ? a.getUuid().equals(that.getUuid()) : that.getUuid() == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (uuid != null ? uuid.hashCode() : 0);
        return result;
    }

}
