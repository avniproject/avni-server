package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@NoRepositoryBean
public interface ReferenceDataRepository<T extends CHSEntity> extends CHSRepository<T>, CustomJpaRepository<T, Long> {
    T findByName(String name);
    T findByNameIgnoreCase(String name);

    Page<T> findPageByIsVoidedFalse(Pageable pageable);
    List<T> findByIsVoidedFalse();

    @RestResource(exported = false)
    List<T> findAllByOrganisationId(Long organisationId);
    @RestResource(exported = false)
    Page<T> findAllByOrganisationId(Long organisationId, Pageable pageable);

    @RestResource(exported = false)
    <S extends T> S save(S entity);

    default T findOne(Long id) {
        return findById(id).orElse(null);
    }

    default T findByUuidOrName(String name, String uuid) {
        return uuid != null ? findByUuid(uuid) : findByName(name);
    }
}
