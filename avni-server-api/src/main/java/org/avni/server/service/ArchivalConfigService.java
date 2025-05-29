package org.avni.server.service;

import org.avni.server.dao.ArchivalConfigRepository;
import org.avni.server.domain.ArchivalConfig;
import org.avni.server.domain.ValidationException;
import org.avni.server.web.contract.ArchivalConfigContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

@Service
public class ArchivalConfigService {
    private final ArchivalConfigRepository archivalConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private static final Logger logger = LoggerFactory.getLogger(ArchivalConfigService.class);

    @Autowired
    public ArchivalConfigService(ArchivalConfigRepository archivalConfigRepository, JdbcTemplate jdbcTemplate) {
        this.archivalConfigRepository = archivalConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public String validateQuery(String query) {
        try {
            MetadataExtractor extractor = new MetadataExtractor();
            jdbcTemplate.query(query, extractor);
            return extractor.getErrorMessage();
        } catch (DataAccessException e) {
            logger.warn(e.getMessage(), e);
            return e.getCause().getMessage();
        }
    }

    public ArchivalConfig saveOrUpdate(ArchivalConfigContract configContract) throws ValidationException {
        String errorMessage = this.validateQuery(configContract.getSqlQuery());
        if (errorMessage != null) {
            logger.warn("Validation failed for query: {}", errorMessage);
            throw new ValidationException(errorMessage);
        }
        ArchivalConfig existingArchivalConfig = archivalConfigRepository.findByUuid(configContract.getUuid());
        ArchivalConfig archivalConfig = existingArchivalConfig != null ? existingArchivalConfig : new ArchivalConfig();
        archivalConfig.setSqlQuery(configContract.getSqlQuery());
        archivalConfig.setRealmQuery(configContract.getRealmQuery());
        archivalConfig.setBatchSize(configContract.getBatchSize());
        archivalConfig.setVoided(configContract.isVoided());
        archivalConfig.assignUUIDIfRequired();
        archivalConfig.updateAudit();
        return archivalConfigRepository.save(archivalConfig);
    }

    public ArchivalConfig getArchivalConfig() {
        List<ArchivalConfig> archivalConfigs = archivalConfigRepository.findByIsVoidedFalse();
        if (archivalConfigs.isEmpty()) {
            return null;
        }
        return archivalConfigs.get(0);
    }

    public List<ArchivalConfig> getAllArchivalConfigs() {
        return archivalConfigRepository.findByIsVoidedFalse();
    }

    public ArchivalConfigContract toContract(ArchivalConfig archivalConfig) {
        ArchivalConfigContract contract = new ArchivalConfigContract();
        contract.setId(archivalConfig.getId());
        contract.setUuid(archivalConfig.getUuid());
        contract.setVoided(archivalConfig.isVoided());
        contract.setSqlQuery(archivalConfig.getSqlQuery());
        contract.setRealmQuery(archivalConfig.getRealmQuery());
        contract.setBatchSize(archivalConfig.getBatchSize());
        return contract;
    }

    public static class MetadataExtractor implements ResultSetExtractor<Void> {
        private String errorMessage;

        private static final List<String> supportDataTypes = List.of("bigint", "integer", "serial");

        @Override
        public Void extractData(ResultSet rs) throws SQLException, DataAccessException {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            if (columnCount != 1) {
                errorMessage = "Query should return one column but returned " + columnCount + " columns.";
            }
            String columnName = metaData.getColumnName(1);
            if (!"id".equalsIgnoreCase(columnName)) {
                errorMessage = "Query should return a column named 'id' but returned '" + columnName + "'.";
            }
            String columnTypeName = metaData.getColumnTypeName(1);
            if (!supportDataTypes.contains(columnTypeName.toLowerCase())) {
                errorMessage = String.format("Query should return a column of type (%s) but returned: %s.",
                        String.join(", ", supportDataTypes), columnTypeName);
            }
            return null;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
