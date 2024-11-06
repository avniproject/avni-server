package org.avni.server.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CHSBaseEntityTest {
    @Test
    public void equals() {
        assertEquals(entity(1l), entity(1l));
        assertNotEquals(entity(1l), entity(2l));
        assertNotEquals(new CHSBaseEntity(), new CHSBaseEntity());
        assertNotEquals(entity(1l), new CHSBaseEntity());
        assertNotEquals(new CHSBaseEntity(), entity(1l));
    }

    private static CHSBaseEntity entity(long id) {
        CHSBaseEntity chsBaseEntity = new CHSBaseEntity();
        chsBaseEntity.setId(id);
        return chsBaseEntity;
    }
}
