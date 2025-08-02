package org.avni.camp.service;

import org.avni.camp.model.EntityMetaData;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing entity metadata for sync operations.
 * This service provides the list of entities that can be synchronized.
 */
@Service
public class EntityMetadataService {
    
    /**
     * Gets the list of entity metadata for sync operations.
     * This would normally be loaded from configuration or discovered from the server.
     * 
     * @return List of EntityMetaData objects representing entities available for sync
     */
    public List<EntityMetaData> getEntityMetaDataList() {
        // This is a placeholder - in a real implementation, this would come from
        // server discovery or configuration files
        return List.of(
            EntityMetaData.createReferenceEntity("Form", "form", 1.0),
            EntityMetaData.createReferenceEntity("Concept", "concept", 1.0),
            EntityMetaData.createReferenceEntity("SubjectType", "subjectType", 1.0),
            EntityMetaData.createTransactionEntity("Individual", "individual", 2.0),
            EntityMetaData.createTransactionEntity("Encounter", "encounter", 2.0)
        );
    }
    
    /**
     * Gets entity metadata for a specific entity type.
     * 
     * @param entityType The entity type to get metadata for
     * @return EntityMetaData for the specified type, or null if not found
     */
    public EntityMetaData getEntityMetaData(String entityType) {
        return getEntityMetaDataList().stream()
            .filter(metadata -> metadata.getEntityName().equalsIgnoreCase(entityType))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets all reference entity metadata.
     * 
     * @return List of reference entities
     */
    public List<EntityMetaData> getReferenceEntities() {
        return getEntityMetaDataList().stream()
            .filter(EntityMetaData::isReferenceEntity)
            .toList();
    }
    
    /**
     * Gets all transaction entity metadata.
     * 
     * @return List of transaction entities
     */
    public List<EntityMetaData> getTransactionEntities() {
        return getEntityMetaDataList().stream()
            .filter(metadata -> !metadata.isReferenceEntity())
            .toList();
    }
}
