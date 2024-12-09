package org.avni.server.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CHSBaseEntityTest {
    @Test
    public void equals() {
        assertEquals(entity(1L), entity(1L));
        assertEquals(entity(1L, "a"), entity(1L, "a"));
        assertEquals(entity(null, "a"), entity(null, "a"));

        assertNotEquals(entity(1L), entity(2L));
        assertNotEquals(new CHSBaseEntity(), new CHSBaseEntity());
        assertNotEquals(entity(1L), new CHSBaseEntity());
        assertNotEquals(new CHSBaseEntity(), entity(1L));
        assertNotEquals(otherTypeOfEntity(1L), entity(1L));
        assertNotEquals(new OtherTypeOfEntity(), new CHSBaseEntity());
        assertEquals(entity(1L), entity(1L));
    }

    private static CHSBaseEntity entity(Long id) {
        return entity(id, null);
    }

    private static CHSBaseEntity entity(Long id, String uuid) {
        CHSBaseEntity chsBaseEntity = new CHSBaseEntity();
        chsBaseEntity.setId(id);
        chsBaseEntity.setUuid(uuid);
        return chsBaseEntity;
    }

    private static OtherTypeOfEntity otherTypeOfEntity(Long id) {
        return otherTypeOfEntity(id, null);
    }

    private static OtherTypeOfEntity otherTypeOfEntity(Long id, String uuid) {
        OtherTypeOfEntity chsBaseEntity = new OtherTypeOfEntity();
        chsBaseEntity.setId(id);
        chsBaseEntity.setUuid(uuid);
        return chsBaseEntity;
    }

    static class OtherTypeOfEntity extends CHSBaseEntity {
    }
}
