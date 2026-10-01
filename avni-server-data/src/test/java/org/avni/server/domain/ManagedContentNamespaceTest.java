package org.avni.server.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedContentNamespaceTest {
    private static final String SHA = "a".repeat(64);

    @Test
    void modelsAcceptsOnlyABinNamedAfterItsHash() {
        assertTrue(ManagedContentNamespace.MODELS.accepts(SHA + ".bin"));
        assertFalse(ManagedContentNamespace.MODELS.accepts(SHA + ".png"));
        assertFalse(ManagedContentNamespace.MODELS.accepts("model.bin"));
        assertFalse(ManagedContentNamespace.MODELS.accepts(SHA.toUpperCase() + ".bin"));
        assertFalse(ManagedContentNamespace.MODELS.accepts(null));
    }

    @Test
    void guidanceAcceptsRealImageExtensionsAndRejectsBin() {
        assertTrue(ManagedContentNamespace.GUIDANCE.accepts(SHA + ".png"));
        assertTrue(ManagedContentNamespace.GUIDANCE.accepts(SHA + ".jpg"));
        assertTrue(ManagedContentNamespace.GUIDANCE.accepts(SHA + ".jpeg"));
        // The device renders these directly; .bin would force the renderer to sniff the content type.
        assertFalse(ManagedContentNamespace.GUIDANCE.accepts(SHA + ".bin"));
        assertFalse(ManagedContentNamespace.GUIDANCE.accepts(SHA + ".svg"));
    }

    @Test
    void guidanceCarriesItsOwnStorageClass() {
        // Not MODEL, so clinical photographs are never routed to wherever the org keeps its AI
        // models; and not DEFAULT, because a DEFAULT routing entry is deliberately ignored, which
        // would leave guidance pictures unroutable.
        assertEquals(StorageDataClass.GUIDANCE, ManagedContentNamespace.GUIDANCE.getDataClass());
        assertEquals(StorageDataClass.MODEL, ManagedContentNamespace.MODELS.getDataClass());
    }

    @Test
    void resolvesANamespaceFromItsPrefix() {
        assertEquals(Optional.of(ManagedContentNamespace.MODELS), ManagedContentNamespace.forPrefix("models"));
        assertEquals(Optional.of(ManagedContentNamespace.GUIDANCE), ManagedContentNamespace.forPrefix("guidance"));
        assertFalse(ManagedContentNamespace.forPrefix("photos").isPresent());
        assertFalse(ManagedContentNamespace.forPrefix("").isPresent());
        assertFalse(ManagedContentNamespace.forPrefix(null).isPresent());
    }

    @Test
    void resolvesANamespaceFromAWholeRelativeKey() {
        assertEquals(Optional.of(ManagedContentNamespace.GUIDANCE),
                ManagedContentNamespace.forRelativeKey("guidance/" + SHA + ".png"));
        assertEquals(Optional.of(ManagedContentNamespace.MODELS),
                ManagedContentNamespace.forRelativeKey("models/" + SHA + ".bin"));
    }

    @Test
    void rejectsAKeyWhoseFileNameDoesNotSuitItsNamespace() {
        assertFalse(ManagedContentNamespace.forRelativeKey("models/" + SHA + ".png").isPresent());
        assertFalse(ManagedContentNamespace.forRelativeKey("guidance/" + SHA + ".bin").isPresent());
        assertFalse(ManagedContentNamespace.forRelativeKey("photos/" + SHA + ".png").isPresent());
        assertFalse(ManagedContentNamespace.forRelativeKey(SHA + ".png").isPresent());
        assertFalse(ManagedContentNamespace.forRelativeKey(null).isPresent());
    }

    @Test
    void doesNotLetATraversalEscapeTheNamespace() {
        assertFalse(ManagedContentNamespace.forRelativeKey("guidance/../models/" + SHA + ".bin").isPresent());
        assertFalse(ManagedContentNamespace.forRelativeKey("guidance/nested/" + SHA + ".png").isPresent());
    }

    @Test
    void tiesAKeyToTheHashItIsNamedAfter() {
        assertTrue(ManagedContentNamespace.GUIDANCE.addresses("guidance/" + SHA + ".png", SHA));
        assertFalse(ManagedContentNamespace.GUIDANCE.addresses("guidance/" + SHA + ".png", "b".repeat(64)));
        assertFalse(ManagedContentNamespace.MODELS.addresses("models/" + SHA + ".bin", "b".repeat(64)));
        assertFalse(ManagedContentNamespace.MODELS.addresses(null, SHA));
        assertFalse(ManagedContentNamespace.MODELS.addresses("models/" + SHA + ".bin", null));
    }

    @Test
    void addressingDoesNotRequireTheStrictUploadFileName() {
        // Records predate the <sha256>.<ext> upload rule, and editing one must not be blocked by a
        // shape only the upload endpoint is responsible for.
        assertTrue(ManagedContentNamespace.MODELS.addresses("models/abc.bin", "abc"));
        assertFalse(ManagedContentNamespace.MODELS.accepts("abc.bin"));
    }

    @Test
    void addressingRejectsANestedPath() {
        assertFalse(ManagedContentNamespace.MODELS.addresses("models/" + SHA + ".bin/evil", SHA));
        assertFalse(ManagedContentNamespace.MODELS.addresses("models/sub/" + SHA + ".bin", SHA));
    }

    @Test
    void listsWhatEachNamespaceAccepts() {
        String forms = ManagedContentNamespace.expectedKeyForms();
        assertTrue(forms.contains("models/<sha256>.bin"));
        assertTrue(forms.contains("guidance/<sha256>.png"));
    }

    @Test
    void buildsTheRelativeKeyItWouldStoreUnder() {
        assertEquals("guidance/" + SHA + ".png", ManagedContentNamespace.GUIDANCE.relativeKeyFor(SHA + ".png"));
        assertEquals("models/" + SHA + ".bin", ManagedContentNamespace.MODELS.relativeKeyFor(SHA + ".bin"));
    }
}
