package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.rest.core.annotation.RestResource;

import java.io.Serializable;
import java.util.List;

@NoRepositoryBean
public interface CustomCHSJpaRepository<T extends CHSEntity, ID extends Serializable> extends JpaRepository<T, ID> {
    Slice<T> findAllAsSlice(Specification<T> specification, Pageable pageable);

    @Override
    @RestResource(exported = false)
    <S extends T> S save(S entity);

    @Override
    @RestResource(exported = false)
    void delete(T entity);

    @Override
    @RestResource(exported = false)
    void deleteAll();

    @Override
    @RestResource(exported = false)
    <S extends T> List<S> saveAll(Iterable<S> entities);

    @Override
    @RestResource(exported = false)
    void flush();

    @Override
    @RestResource(exported = false)
    <S extends T> S saveAndFlush(S entity);

    @Override
    @RestResource(exported = false)
    void deleteInBatch(Iterable<T> entities);

    @Override
    @RestResource(exported = false)
    void deleteAllInBatch();
}
