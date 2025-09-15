package org.avni.server.service;

import org.avni.server.dao.StorageManagementConfigRepository;
import org.avni.server.domain.StorageManagementConfig;
import org.avni.server.domain.ValidationException;
import org.avni.server.web.contract.StorageManagementConfigContract;
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
public class StorageManagementConfigService {
    private final StorageManagementConfigRepository storageManagementConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private static final Logger logger = LoggerFactory.getLogger(StorageManagementConfigService.class);

    @Autowired
    public StorageManagementConfigService(StorageManagementConfigRepository storageManagementConfigRepository, JdbcTemplate jdbcTemplate) {
        this.storageManagementConfigRepository = storageManagementConfigRepository;
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

    public StorageManagementConfig saveOrUpdate(StorageManagementConfigContract configContract) throws ValidationException {
        String errorMessage = this.validateQuery(configContract.getSqlQuery());
        if (errorMessage != null) {
            logger.warn("Validation failed for query: {}", errorMessage);
            throw new ValidationException(errorMessage);
        }
        StorageManagementConfig existingStorageManagementConfig = storageManagementConfigRepository.findByUuid(configContract.getUuid());
        StorageManagementConfig storageManagementConfig = existingStorageManagementConfig != null ? existingStorageManagementConfig : new StorageManagementConfig();
        storageManagementConfig.setSqlQuery(configContract.getSqlQuery());
        storageManagementConfig.setRealmQuery(configContract.getRealmQuery());
        storageManagementConfig.setBatchSize(configContract.getBatchSize());
        storageManagementConfig.setVoided(configContract.isVoided());
        storageManagementConfig.assignUUIDIfRequired();
        storageManagementConfig.updateAudit();
        return storageManagementConfigRepository.save(storageManagementConfig);
    }

    public StorageManagementConfig getStorageManagementConfig() {
        List<StorageManagementConfig> storageManagementConfigs = storageManagementConfigRepository.findByIsVoidedFalse();
        if (storageManagementConfigs.isEmpty()) {
            return null;
        }
        return storageManagementConfigs.get(0);
    }

    public List<StorageManagementConfig> getAllStorageManagementConfigs() {
        return storageManagementConfigRepository.findByIsVoidedFalse();
    }

    public StorageManagementConfigContract toContract(StorageManagementConfig storageManagementConfig) {
        StorageManagementConfigContract contract = new StorageManagementConfigContract();
        contract.setId(storageManagementConfig.getId());
        contract.setUuid(storageManagementConfig.getUuid());
        contract.setVoided(storageManagementConfig.isVoided());
        contract.setSqlQuery(storageManagementConfig.getSqlQuery());
        contract.setRealmQuery(storageManagementConfig.getRealmQuery());
        contract.setBatchSize(storageManagementConfig.getBatchSize());
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
