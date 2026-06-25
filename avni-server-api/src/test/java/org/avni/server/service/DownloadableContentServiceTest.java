package org.avni.server.service;

import org.avni.server.dao.DownloadableContentRepository;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DownloadableContentServiceTest {

    @Mock
    private DownloadableContentRepository downloadableContentRepository;

    private DownloadableContentService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new DownloadableContentService(downloadableContentRepository);
        when(downloadableContentRepository.save(any(DownloadableContent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    public void createAssignsUuidAndPersistsFields() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engine", "onnx");

        DownloadableContent saved = service.createOrUpdate(buildRequest(null, "Edge Model", "edgeModel", payload));

        assertNotNull("a new content must get a uuid", saved.getUuid());
        assertEquals("Edge Model", saved.getName());
        assertEquals("edgeModel", saved.getCategory());
        assertEquals("models/abc.bin", saved.getContentKey());
        assertEquals("abc", saved.getSha256());
        assertTrue(saved.isNeedsKey());
        assertNotNull(saved.getPayload());
        assertEquals("onnx", saved.getPayload().get("engine"));
        verify(downloadableContentRepository, times(1)).save(any(DownloadableContent.class));
    }

    @Test
    public void createTrimsNameCategoryContentKeyAndSha256() {
        DownloadableContentRequest request = new DownloadableContentRequest();
        request.setName("  Edge Model  ");
        request.setCategory("  edgeModel  ");
        request.setContentKey("  models/abc.bin  ");
        request.setSha256("  abc  ");

        DownloadableContent saved = service.createOrUpdate(request);

        assertEquals("Edge Model", saved.getName());
        assertEquals("edgeModel", saved.getCategory());
        assertEquals("models/abc.bin", saved.getContentKey());
        assertEquals("abc", saved.getSha256());
    }

    @Test
    public void createRejectsBlankName() {
        DownloadableContentRequest request = new DownloadableContentRequest();
        request.setName("   ");
        request.setCategory("edgeModel");
        try {
            service.createOrUpdate(request);
            fail("Expected BadRequestError for blank name");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("Name is required"));
        }
        verify(downloadableContentRepository, times(0)).save(any(DownloadableContent.class));
    }

    @Test
    public void createRejectsBlankCategory() {
        DownloadableContentRequest request = new DownloadableContentRequest();
        request.setName("Edge Model");
        request.setCategory("   ");
        try {
            service.createOrUpdate(request);
            fail("Expected BadRequestError for blank category");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("Category is required"));
        }
        verify(downloadableContentRepository, times(0)).save(any(DownloadableContent.class));
    }

    @Test
    public void updateByUuidLoadsExistingAndMutatesIt() {
        String uuid = "existing-uuid";
        DownloadableContent existing = new DownloadableContent();
        existing.setUuid(uuid);
        existing.setName("Old Name");
        existing.setCategory("edgeModel");
        when(downloadableContentRepository.findByUuid(uuid)).thenReturn(existing);

        DownloadableContentRequest request = buildRequest(uuid, "New Name", "edgeModel", null);
        DownloadableContent saved = service.createOrUpdate(request);

        assertSame("update must mutate the loaded entity, not create a new one", existing, saved);
        assertEquals("New Name", saved.getName());
        assertEquals(uuid, saved.getUuid());
    }

    @Test
    public void createWithSuppliedUuidUsesItWhenNotFound() {
        String uuid = "client-supplied-uuid";
        when(downloadableContentRepository.findByUuid(uuid)).thenReturn(null);

        DownloadableContent saved = service.createOrUpdate(buildRequest(uuid, "Edge Model", "edgeModel", null));

        assertEquals(uuid, saved.getUuid());
    }

    @Test
    public void payloadIsNullWhenRequestPayloadEmpty() {
        DownloadableContent saved = service.createOrUpdate(buildRequest(null, "Edge Model", "edgeModel", new LinkedHashMap<>()));
        assertNull(saved.getPayload());
    }

    @Test
    public void createRejectsDuplicateNameOnDifferentEntity() {
        DownloadableContent other = new DownloadableContent();
        other.assignUUID();
        other.setName("Edge Model");
        when(downloadableContentRepository.findByNameIgnoreCaseAndIsVoidedFalse("Edge Model")).thenReturn(other);

        try {
            service.createOrUpdate(buildRequest(null, "Edge Model", "edgeModel", null));
            fail("Expected BadRequestError for duplicate name");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("already exists"));
            assertTrue(e.getMessage().contains("Edge Model"));
        }
        verify(downloadableContentRepository, times(0)).save(any(DownloadableContent.class));
    }

    @Test
    public void reSaveWithSameNameOnSelfIsAllowed() {
        String uuid = "self-uuid";
        DownloadableContent existing = new DownloadableContent();
        existing.setUuid(uuid);
        existing.setName("Edge Model");
        when(downloadableContentRepository.findByUuid(uuid)).thenReturn(existing);
        // the name belongs to the same entity being saved
        when(downloadableContentRepository.findByNameIgnoreCaseAndIsVoidedFalse("Edge Model")).thenReturn(existing);

        DownloadableContent saved = service.createOrUpdate(buildRequest(uuid, "Edge Model", "edgeModel", null));
        assertEquals("Edge Model", saved.getName());
    }

    @Test
    public void deleteContentVoidsAndManglesName() {
        String uuid = "to-delete";
        DownloadableContent existing = new DownloadableContent();
        existing.setUuid(uuid);
        existing.setName("Edge Model");
        when(downloadableContentRepository.findByUuid(uuid)).thenReturn(existing);

        service.deleteContent(uuid);

        assertTrue("delete must void the entity", existing.isVoided());
        assertTrue("voided name must be mangled to free up the unique name",
                existing.getName().contains("Edge Model"));
        assertFalse("voided name must differ from the live name", existing.getName().equals("Edge Model"));
        verify(downloadableContentRepository, times(1)).save(existing);
    }

    @Test
    public void deleteContentRejectsUnknownUuid() {
        when(downloadableContentRepository.findByUuid("nope")).thenReturn(null);
        try {
            service.deleteContent("nope");
            fail("Expected BadRequestError for unknown uuid");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void isNonScopeEntityChangedDelegatesToRepository() {
        DateTime cutoff = DateTime.now().minusDays(1);
        when(downloadableContentRepository.existsByLastModifiedDateTimeGreaterThan(cutoff)).thenReturn(true);
        assertTrue(service.isNonScopeEntityChanged(cutoff));

        when(downloadableContentRepository.existsByLastModifiedDateTimeGreaterThan(cutoff)).thenReturn(false);
        assertFalse(service.isNonScopeEntityChanged(cutoff));
    }

    private DownloadableContentRequest buildRequest(String uuid, String name, String category, Map<String, Object> payload) {
        DownloadableContentRequest request = new DownloadableContentRequest();
        request.setUuid(uuid);
        request.setName(name);
        request.setCategory(category);
        request.setContentKey("models/abc.bin");
        request.setSha256("abc");
        request.setNeedsKey(true);
        request.setPayload(payload);
        return request;
    }
}
