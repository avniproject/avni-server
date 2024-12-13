package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.avni.server.dao.search.EncounterSearchQueryBuilder;
import org.avni.server.dao.search.SqlQuery;
import org.avni.server.domain.Encounter;
import org.avni.server.web.api.EncounterSearchRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EncounterSearchRepository extends RoleSwitchableRepository {
    public EncounterSearchRepository(EntityManager entityManager) {
        super(entityManager);
    }

    @Transactional
    public List<Encounter> search(EncounterSearchRequest searchRequest) {
        try {
            setRoleToNone();
            SqlQuery query = new EncounterSearchQueryBuilder().withRequest(searchRequest).build();
            Query sql = entityManager.createNativeQuery(query.getSql(), Encounter.class);
            query.getParameters().forEach((name, value) -> {
                sql.setParameter(name, value);
            });

            return sql.getResultList();
        } finally {
            setRoleBackToUser();
        }
    }

    @Transactional
    public long getCount(EncounterSearchRequest searchRequest) {
        try {
            setRoleToNone();
            SqlQuery query = new EncounterSearchQueryBuilder().withRequest(searchRequest).forCount().build();
            Query sql = entityManager.createNativeQuery(query.getSql());

            query.getParameters().forEach((name, value) -> {
                sql.setParameter(name, value);
            });
            return (Long) sql.getSingleResult();
        } finally {
            setRoleBackToUser();
        }
    }
}
