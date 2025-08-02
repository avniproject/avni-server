package org.avni.sync.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EntitySyncStatus model.
 * Demonstrates testing approach for the sync models.
 */
class EntitySyncStatusTest {
    
    @Test
    void testCreateEntitySyncStatus() {
        String entityName = "Individual";
        LocalDateTime loadedSince = LocalDateTime.now();
        String uuid = "test-uuid";
        String entityTypeUuid = "type-uuid";
        
        EntitySyncStatus status = EntitySyncStatus.create(entityName, loadedSince, uuid, entityTypeUuid);
        
        assertNotNull(status);
        assertEquals(entityName, status.getEntityName());
        assertEquals(loadedSince, status.getLoadedSince());
        assertEquals(uuid, status.getUuid());
        assertEquals(entityTypeUuid, status.getEntityTypeUuid());
        assertFalse(status.isVoided());
        assertNotNull(status.getLastModifiedDateTime());
    }
    
    @Test
    void testCreateWithNullValues() {
        String entityName = "Individual";
        
        EntitySyncStatus status = EntitySyncStatus.create(entityName, null, null, null);
        
        assertNotNull(status);
        assertEquals(entityName, status.getEntityName());
        assertEquals(EntitySyncStatus.REALLY_OLD_DATE, status.getLoadedSince());
        assertNotNull(status.getUuid());
        assertNull(status.getEntityTypeUuid());
    }
    
    @Test
    void testDefaultConstructor() {
        EntitySyncStatus status = new EntitySyncStatus();
        
        assertNotNull(status);
        assertNotNull(status.getUuid());
        assertEquals(EntitySyncStatus.REALLY_OLD_DATE, status.getLoadedSince());
        assertNotNull(status.getLastModifiedDateTime());
        assertFalse(status.isVoided());
    }
    
    @Test
    void testEqualsAndHashCode() {
        String uuid1 = "uuid-1";
        String uuid2 = "uuid-2";
        
        EntitySyncStatus status1 = new EntitySyncStatus();
        status1.setUuid(uuid1);
        
        EntitySyncStatus status2 = new EntitySyncStatus();
        status2.setUuid(uuid1);
        
        EntitySyncStatus status3 = new EntitySyncStatus();
        status3.setUuid(uuid2);
        
        assertEquals(status1, status2);
        assertNotEquals(status1, status3);
        assertEquals(status1.hashCode(), status2.hashCode());
        assertNotEquals(status1.hashCode(), status3.hashCode());
    }
    
    @Test
    void testToString() {
        EntitySyncStatus status = EntitySyncStatus.create("Individual", LocalDateTime.now(), "test-uuid", "type-uuid");
        
        String toString = status.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("Individual"));
        assertTrue(toString.contains("test-uuid"));
        assertTrue(toString.contains("type-uuid"));
    }
}