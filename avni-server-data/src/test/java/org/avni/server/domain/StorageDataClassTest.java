package org.avni.server.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageDataClassTest {

    @Test
    void leadingModelsPrefixIsModel() {
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("models/abc123.bin"));
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("/models/abc123.bin"));
    }

    @Test
    void orgScopedModelsSegmentIsModel() {
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("orgdir/models/abc123.bin"));
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("/orgdir/models/abc123.bin"));
    }

    @Test
    void fullMediaUrlWithModelsSegmentIsModel() {
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("https://bucket.s3.ap-south-1.amazonaws.com/orgdir/models/abc.bin"));
    }

    @Test
    void normalMediaKeyIsDefault() {
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("orgdir/0c5b-uuid.jpg"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("orgdir/profile-pics/abc.png"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("https://bucket.s3.ap-south-1.amazonaws.com/orgdir/photo.jpg"));
    }

    @Test
    void substringModelsThatIsNotAPathSegmentIsDefault() {
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("mymodels/abc.bin"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("models-archive/abc.bin"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("orgdir/photo-models.png"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("orgdir/3dmodels.bin"));
    }

    @Test
    void modelsOnlyInTheQueryStringOfADefaultUrlIsDefault() {
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("https://bucket.s3.ap-south-1.amazonaws.com/orgdir/photo.jpg?prefix=/models/x"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("https://cdn.example.com/orgdir/photo.jpg#/models/frag"));
    }

    @Test
    void orgDirNamedModelsStillClassifiesModelObjectsAsModel() {
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("models/deadbeef.bin"));
        assertEquals(StorageDataClass.MODEL,
                StorageDataClass.dataClassForKey("https://bucket.s3.ap-south-1.amazonaws.com/models/deadbeef.bin"));
    }

    @Test
    void leadingGuidancePrefixIsGuidance() {
        assertEquals(StorageDataClass.GUIDANCE,
                StorageDataClass.dataClassForKey("guidance/abc123.png"));
        assertEquals(StorageDataClass.GUIDANCE,
                StorageDataClass.dataClassForKey("/guidance/abc123.png"));
    }

    @Test
    void orgScopedGuidanceSegmentIsGuidance() {
        assertEquals(StorageDataClass.GUIDANCE,
                StorageDataClass.dataClassForKey("orgdir/guidance/abc123.jpg"));
        assertEquals(StorageDataClass.GUIDANCE,
                StorageDataClass.dataClassForKey("https://bucket.s3.ap-south-1.amazonaws.com/orgdir/guidance/abc.png"));
    }

    @Test
    void substringGuidanceThatIsNotAPathSegmentIsDefault() {
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("myguidance/abc.png"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("guidance-archive/abc.png"));
        assertEquals(StorageDataClass.DEFAULT,
                StorageDataClass.dataClassForKey("orgdir/photo-guidance.png"));
    }

    @Test
    void guidanceAndModelsAreDistinctClassesSoTheyRouteSeparately() {
        assertEquals(StorageDataClass.MODEL, StorageDataClass.dataClassForKey("orgdir/models/abc.bin"));
        assertEquals(StorageDataClass.GUIDANCE, StorageDataClass.dataClassForKey("orgdir/guidance/abc.png"));
    }

    @Test
    void everyRoutableClassHasItsOwnConfigName() {
        // storageBackends is keyed by config name, so a duplicate would make one class unroutable.
        assertEquals("model", StorageDataClass.MODEL.getConfigName());
        assertEquals("guidance", StorageDataClass.GUIDANCE.getConfigName());
        assertEquals("default", StorageDataClass.DEFAULT.getConfigName());
    }

    @Test
    void nullKeyIsDefault() {
        assertEquals(StorageDataClass.DEFAULT, StorageDataClass.dataClassForKey(null));
    }
}
