package org.avni.server.dao;

import jakarta.persistence.EntityManager;
import org.avni.server.domain.CustomQuery;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.CustomQueryRequest;
import org.avni.server.web.response.CustomQueryResponse;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.flywaydb.core.internal.util.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class QueryRepository extends RoleSwitchableRepository {
    public static final String ORG_ID = "org_id";
    public static final String ORG_DB_USER = "org_db_user";
    public static final String ORG_SCHEMA_NAME = "org_schema_name";
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final CustomQueryRepository customQueryRepository;
    private final ErrorBodyBuilder errorBodyBuilder;

    @Autowired
    public QueryRepository(@Qualifier("externalQueryJdbcTemplate") NamedParameterJdbcTemplate externalQueryJdbcTemplate,
                           CustomQueryRepository customQueryRepository, ErrorBodyBuilder errorBodyBuilder,
                           EntityManager entityManager) {
        super(entityManager);
        this.namedParameterJdbcTemplate = externalQueryJdbcTemplate;
        this.customQueryRepository = customQueryRepository;
        this.errorBodyBuilder = errorBodyBuilder;
    }

    QueryRepository(NamedParameterJdbcTemplate externalQueryJdbcTemplate, CustomQueryRepository customQueryRepository, EntityManager entityManager) {
        this(externalQueryJdbcTemplate, customQueryRepository, ErrorBodyBuilder.createForTest(), entityManager);
    }

    public ResponseEntity<?> runQuery(CustomQueryRequest customQueryRequest) {
        CustomQuery customQuery = customQueryRepository.findAllByName(customQueryRequest.getName());
        if (customQuery == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(String.format("Query not found with name %s", customQueryRequest.getName()));
        }
        try {
            List<Map<String, Object>> queryResult = namedParameterJdbcTemplate.queryForList(customQuery.getQuery(), getCustomQueryParamsWithOrgContext(customQueryRequest));
            return ResponseEntity.ok(new CustomQueryResponse(queryResult));
        } catch (DataAccessException e) {
            String errorMessage = ExceptionUtils.getRootCause(e).getMessage();
            if (errorMessage.equals("ERROR: canceling statement due to user request")) {
                errorMessage = "Query took more time to return the result";
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(String.format("Error while executing the query message : \"%s\"", errorMessage)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(String.format("Encountered some error while executing the query message %s", e.getMessage())));
        }
    }

    private Map<String, Object> getCustomQueryParamsWithOrgContext(CustomQueryRequest customQueryRequest) {
        Map<String, Object> customQueryParamsWithOrgContext = new HashMap<>(customQueryRequest.getQueryParams());
        customQueryParamsWithOrgContext.put(ORG_ID, UserContextHolder.getOrganisation().getId());
        customQueryParamsWithOrgContext.put(ORG_DB_USER, UserContextHolder.getOrganisation().getDbUser());
        customQueryParamsWithOrgContext.put(ORG_SCHEMA_NAME, UserContextHolder.getOrganisation().getSchemaName());
        return customQueryParamsWithOrgContext;
    }
}
