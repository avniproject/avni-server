package org.avni.server.dao;

import org.avni.server.domain.CHSEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TransactionalDataRepository<T extends CHSEntity> extends CHSRepository<T>, CustomCHSJpaRepository<T, Long>, JpaSpecificationExecutor<T>{
    default T findOne(Long id) {
        return findById(id).orElse(null);
    }
}
