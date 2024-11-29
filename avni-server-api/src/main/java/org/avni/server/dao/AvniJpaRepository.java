package org.avni.server.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;

@NoRepositoryBean
public interface AvniJpaRepository<T, ID> extends JpaRepository<T, ID> {
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
