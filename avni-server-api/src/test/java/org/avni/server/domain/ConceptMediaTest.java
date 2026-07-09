package org.avni.server.domain;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConceptMediaTest {
    @Test
    public void buildsIndexedBundleFileName() {
        String name = ConceptMedia.buildBundleFileName(
                "concept-uuid", ConceptMedia.MediaType.Image, 5, "abc.jpg");
        assertEquals("concept-uuid--Image--005--abc.jpg", name);
    }

    @Test
    public void parsesIndexedBundleFileName() {
        ConceptMedia.BundleFileNameParts parts =
                ConceptMedia.parseBundleFileName("concept-uuid--Image--005--abc.jpg");
        assertEquals("concept-uuid", parts.conceptUuid);
        assertEquals(ConceptMedia.MediaType.Image, parts.type);
        assertEquals(Integer.valueOf(5), parts.index);
        assertEquals("abc.jpg", parts.s3FileName);
    }

    @Test
    public void parsesLegacyThreePartBundleFileNameWithNullIndex() {
        ConceptMedia.BundleFileNameParts parts =
                ConceptMedia.parseBundleFileName("concept-uuid--Video--xyz.mp4");
        assertEquals("concept-uuid", parts.conceptUuid);
        assertEquals(ConceptMedia.MediaType.Video, parts.type);
        assertNull(parts.index);
        assertEquals("xyz.mp4", parts.s3FileName);
    }

    @Test
    public void parseToleratesSeparatorInsideFileName() {
        ConceptMedia.BundleFileNameParts parts =
                ConceptMedia.parseBundleFileName("uuid--Image--002--a--b.jpg");
        assertEquals(3, "002".length());
        assertEquals(Integer.valueOf(2), parts.index);
        assertEquals("a--b.jpg", parts.s3FileName);
    }
}
