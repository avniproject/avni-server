package org.avni.server.dao;

import org.avni.server.dao.search.SearchBuilder;
import org.avni.server.dao.search.SqlQuery;
import org.avni.server.domain.SubjectType;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManager;

import jakarta.persistence.*;

import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
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

    private List<Map<String, Object>> convertTuplesToMap(List<?> tuples) {
        List<Map<String, Object>> result = new ArrayList<>();

        tuples.forEach(object -> {
            if (object instanceof Tuple single) {
                Map<String, Object> tempMap = new HashMap<>();
                for (TupleElement<?> key : single.getElements()) {
                    tempMap.put(key.getAlias(), single.get(key));
                }
                result.add(tempMap);
            } else {
                throw new RuntimeException("Query should return instance of Tuple");
            }
        });

        return result;
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
            query.getParameters().forEach((name, value) -> {
                sql.setParameter(name, value);
            });
            List resultList = sql.getResultList();
            return convertTuplesToMap(resultList);
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
