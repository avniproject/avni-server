package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hibernate.query.sql.internal.NativeQueryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class GeneralRepository {
    private final EntityManager entityManager;

    @Autowired
    public GeneralRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long execute(String sql) {
        Query query = entityManager.createNativeQuery(sql);
        NativeQueryImpl nativeQuery = (NativeQueryImpl) query;
        return nativeQuery.getResultCount();
    }
}
