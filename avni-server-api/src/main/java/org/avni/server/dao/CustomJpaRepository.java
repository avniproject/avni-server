package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.Serializable;

@NoRepositoryBean
@PreAuthorize(value = "hasAnyAuthority('user', 'admin')")
public interface CustomJpaRepository<T extends CHSEntity, ID extends Serializable> extends JpaRepository<T, ID> {
    Slice<T> findAllAsSlice(Specification<T> specification, Pageable pageable);
}
