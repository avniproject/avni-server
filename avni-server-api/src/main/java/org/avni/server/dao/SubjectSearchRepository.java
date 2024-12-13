package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.avni.server.dao.search.SearchBuilder;
import org.avni.server.dao.search.SqlQuery;
import org.avni.server.domain.SubjectType;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.hibernate.query.sql.internal.NativeQueryImpl;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Repository
public class SubjectSearchRepository extends RoleSwitchableRepository {
    private final Logger logger = LoggerFactory.getLogger(SubjectSearchRepository.class);
    private final SubjectTypeRepository subjectTypeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public SubjectSearchRepository(EntityManager entityManager, SubjectTypeRepository subjectTypeRepository) {
        super(entityManager);
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Transactional
    public List<Map<String, Object>> search(SubjectSearchRequest searchRequest, SearchBuilder searchBuilder) {
        SubjectType subjectType = StringUtils.isEmpty(searchRequest.getSubjectType()) ? null : subjectTypeRepository.findByUuid(searchRequest.getSubjectType());
        SqlQuery query = searchBuilder.getSQLResultQuery(searchRequest, subjectType);
        try {
            setRoleToNone();
            logger.debug("Executing query: " + query.getSql());
            logger.debug("Parameters: " + query.getParameters());
            Query sql = entityManager.createNativeQuery(query.getSql());
            NativeQueryImpl nativeQuery = (NativeQueryImpl) sql;
            nativeQuery.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
            query.getParameters().forEach((name, value) -> {
                sql.setParameter(name, value);
            });
            return sql.getResultList();
        } finally {
            setRoleBackToUser();
        }
    }

    @Transactional
    public BigInteger getTotalCount(SubjectSearchRequest searchRequest, SearchBuilder searchBuilder) {
        SubjectType subjectType = StringUtils.isEmpty(searchRequest.getSubjectType()) ? null : subjectTypeRepository.findByUuid(searchRequest.getSubjectType());
        SqlQuery query = searchBuilder.getSQLCountQuery(searchRequest, subjectType);
        try {
            setRoleToNone();
            Query sql = entityManager.createNativeQuery(query.getSql());
            query.getParameters().forEach((name, value) -> {
                sql.setParameter(name, value);
            });

            return (BigInteger) sql.getSingleResult();
        } finally {
            setRoleBackToUser();
        }
    }
}
