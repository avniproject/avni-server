package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.Serializable;
import java.util.List;

public class CustomJpaRepositoryImpl<T extends CHSEntity, ID extends Serializable> extends SimpleJpaRepository<T, ID> implements CustomJpaRepository<T, ID> {

    public CustomJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
    }

    @Override
    public Slice<T> findAllAsSlice(Specification<T> specification, Pageable pageable) {
        TypedQuery<T> query = getQuery(specification, pageable);
        return pageable.isUnpaged() ? new SliceImpl<>(query.getResultList()) : readSlice(query, pageable, specification);
    }

    private Slice<T> readSlice(TypedQuery<T> query, Pageable pageable, Specification<T> specification){
        if (pageable.isPaged()){
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize() + 1); // We should get 1 more row to understand there is a next page or not
        }
        List<T> content = query.getResultList();
        boolean hasNextPage = content.size() > pageable.getPageSize();
        if (content.size() > pageable.getPageSize()){ // If the result set contains 1 more row than the desired page count, we normalize the result set
            content = content.subList(0, pageable.getPageSize());
        }
        return new SliceImpl<>(content, pageable, hasNextPage);
    }
}