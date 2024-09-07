package org.avni.server.util;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CreateCollectionRequestUtilTest {
    @Test
    public void hasOnlyTrailingEmptyStrings() {
        assertTrue(CollectionUtil.hasOnlyTrailingEmptyStrings(Arrays.asList("a", "b", "")));
        assertTrue(CollectionUtil.hasOnlyTrailingEmptyStrings(Arrays.asList("a", "b", "", "")));
        assertTrue(CollectionUtil.hasOnlyTrailingEmptyStrings(Arrays.asList("a", "b", "c")));
        assertFalse(CollectionUtil.hasOnlyTrailingEmptyStrings(Arrays.asList("a", "", "c")));
        assertFalse(CollectionUtil.hasOnlyTrailingEmptyStrings(Arrays.asList("", "b", "c")));
    }
}
