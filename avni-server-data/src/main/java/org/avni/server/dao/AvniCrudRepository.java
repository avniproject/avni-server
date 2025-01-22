package org.avni.server.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

@RepositoryRestResource
@NoRepositoryBean
public interface AvniCrudRepository<T, ID> extends CrudRepository<T, ID> {

    /**
     * Saves a given entity. Use the returned instance for further operations as
     * the save operation might have changed the entity instance completely.
     *
     * @param entity
     * @return the saved entity
     */
    @Override
    @RestResource(exported = false)
    <S extends T> S save(S entity);

    /**
     * Saves all given entities.
     *
     * @param entities
     * @return the saved entities
     * @throws IllegalArgumentException
     *             in case the given entity is {@literal null}.
     */
    @Override
    @RestResource(exported = false)
    <S extends T> Iterable<S> saveAll(Iterable<S> entities);

    /**
     * Deletes the entity with the given id.
     *
     * @param id
     *            must not be {@literal null}.
     * @throws IllegalArgumentException
     *             in case the given {@code id} is {@literal null}
     */
    @Override
    @RestResource(exported = false)
    void deleteById(ID id);

    /**
     * Deletes a given entity.
     *
     * @param entity
     * @throws IllegalArgumentException
     *             in case the given entity is {@literal null}.
     */
    @Override
    @RestResource(exported = false)
    void delete(T entity);

    /**
     * Deletes the given entities.
     *
     * @param entities
     * @throws IllegalArgumentException
     *             in case the given {@link Iterable} is {@literal null}.
     */
    @Override
    @RestResource(exported = false)
    void deleteAll(Iterable<? extends T> entities);

    /**
     * Deletes all entities managed by the repository.
     */
    @Override
    @RestResource(exported = false)
    void deleteAll();

}
