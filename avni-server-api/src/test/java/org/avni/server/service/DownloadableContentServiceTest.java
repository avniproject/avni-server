package org.avni.server.service;

import org.avni.server.dao.DownloadableContentRepository;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.domain.JsonObject;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    // A rule finds a guidance picture by position and picture type, and the phone takes the first match, so a
    // second record for the same pair could show a stale picture. avniproject/avni-webapp#1798.
    @Test
    public void createRejectsASecondGuidancePictureForTheSamePositionAndType() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "Left buccal mucosa - reference", 3, "reckoner");
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));

        try {
            service.createOrUpdate(guidanceRequest(null, "Left buccal mucosa - again", 3, "reckoner"));
            fail("Expected BadRequestError for a second picture at the same position and type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("Left buccal mucosa - reference"));
            assertTrue(e.getMessage().contains("position 3"));
        }
        verify(downloadableContentRepository, times(0)).save(any(DownloadableContent.class));
    }

    @Test
    public void theSamePositionIsAllowedForTheOtherPictureType() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "Left buccal mucosa - reference", 3, "reckoner");
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));

        DownloadableContent saved = service.createOrUpdate(guidanceRequest(null, "Left buccal mucosa - outline", 3, "overlay"));

        assertEquals("overlay", saved.getPayload().get("kind"));
    }

    @Test
    public void editingAGuidancePictureIsNotItsOwnDuplicate() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "Left buccal mucosa - reference", 3, "reckoner");
        when(downloadableContentRepository.findByUuid("existing-uuid")).thenReturn(existing);
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));

        DownloadableContent saved = service.createOrUpdate(guidanceRequest("existing-uuid", "Left buccal mucosa", 3, "reckoner"));

        assertEquals("Left buccal mucosa", saved.getName());
    }

    @Test
    public void editingAGuidancePictureIntoAPositionAndTypeAnotherHoldsIsRefused() {
        DownloadableContent reference = guidanceRecord("reference-uuid", "Left buccal mucosa - reference", 3, "reckoner");
        DownloadableContent outline = guidanceRecord("outline-uuid", "Left buccal mucosa - outline", 3, "overlay");
        when(downloadableContentRepository.findByUuid("outline-uuid")).thenReturn(outline);
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(reference, outline));

        try {
            service.createOrUpdate(guidanceRequest("outline-uuid", "Left buccal mucosa - outline", 3, "reckoner"));
            fail("Expected BadRequestError for moving a picture into a pair another picture holds");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("Left buccal mucosa - reference"));
        }
        verify(downloadableContentRepository, times(0)).save(any(DownloadableContent.class));
    }

    // A stored payload and a request can carry the same whole number as different Number types.
    @Test
    public void positionsAreComparedAsNumbers() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "Left buccal mucosa - reference", 3L, "reckoner");
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));

        try {
            service.createOrUpdate(guidanceRequest(null, "Left buccal mucosa - again", 3, "reckoner"));
            fail("Expected BadRequestError for a second picture at the same position and type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("position 3"));
        }
    }

    @Test
    public void voidingAGuidancePictureIsNotCheckedForDuplicates() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "Left buccal mucosa - reference", 3, "reckoner");
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));
        DownloadableContentRequest request = guidanceRequest(null, "Left buccal mucosa - again", 3, "reckoner");
        request.setVoided(true);

        assertTrue(service.createOrUpdate(request).isVoided());
    }

    @Test
    public void onlyGuidancePicturesAreCheckedForPositionAndType() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engine", "onnx");
        payload.put("sequence", 3);
        payload.put("kind", "reckoner");

        service.createOrUpdate(buildRequest(null, "Edge Model", "edgeModel", payload));

        verify(downloadableContentRepository, times(0)).findAllByCategoryAndIsVoidedFalse(anyString());
    }

    // QA's case on 9 Oct: the text "91" never equals the number 91, so a second Reference photo for 91 saved. The phone
    // matches a position exactly too, so a picture saved with a text position is one no rule can ever find.
    @Test
    public void aGuidancePictureWithItsPositionAsTextIsRefused() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "QA1798 91 reckoner", 91, "reckoner");
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));

        assertGuidanceRefused(guidanceRequest(null, "QA dup text position", "91", "reckoner"), "position (a whole number, 1 or more)");
    }

    @Test
    public void aGuidancePictureWithItsPositionAsTextIsRefusedWhenThePositionIsFree() {
        assertGuidanceRefused(guidanceRequest(null, "Left buccal mucosa - reference", "3", "reckoner"), "position (a whole number, 1 or more)");
    }

    @Test
    public void aPositionThatIsNotAWholeNumberFromOneIsRefused() {
        // JSON 1e400 arrives as an infinite Double, which the JSON column would store as the text "Infinity".
        for (Object position : Arrays.asList(2.5, 0, -1, "abc", null, Double.POSITIVE_INFINITY, Double.NaN)) {
            assertGuidanceRefused(guidanceRequest(null, "Left buccal mucosa - reference", position, "reckoner"), "position (a whole number, 1 or more)");
        }
    }

    // 91.0 is the same position as 91 to the phone.
    @Test
    public void aPositionWrittenAsAWholeDecimalIsSaved() {
        DownloadableContent saved = service.createOrUpdate(guidanceRequest(null, "Left buccal mucosa - reference", 91.0, "reckoner"));

        assertEquals(91.0, saved.getPayload().get("sequence"));
    }

    @Test
    public void aPositionWrittenAsAWholeDecimalCollidesWithThatWholeNumber() {
        DownloadableContent existing = guidanceRecord("existing-uuid", "QA1798 91 reckoner", 91, "reckoner");
        when(downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage")).thenReturn(List.of(existing));

        assertGuidanceRefused(guidanceRequest(null, "Left buccal mucosa - again", 91.0, "reckoner"), "QA1798 91 reckoner");
    }

    @Test
    public void aBlankMissingOrNonTextSiteNameIsRefused() {
        for (Object site : Arrays.asList("   ", null, 5)) {
            Map<String, Object> payload = guidancePayload(3, "reckoner");
            payload.put("site", site);
            assertGuidanceRefused(guidanceRequest(null, "Left buccal mucosa - reference", payload), "site name (a non-empty string)");
        }
    }

    // Matched exactly on the phone and in the duplicate check, so "Reckoner" would slip past both.
    @Test
    public void aPictureTypeOtherThanReferencePhotoOrFramingOutlineIsRefused() {
        for (Object kind : Arrays.asList("Reckoner", "photo", null, 1)) {
            assertGuidanceRefused(guidanceRequest(null, "Left buccal mucosa - reference", 3, kind), "picture type (Reference photo or Framing outline)");
        }
    }

    @Test
    public void aGuidancePictureWithNoDetailsIsRefusedInTheScreensWords() {
        try {
            service.createOrUpdate(guidanceRequest(null, "Left buccal mucosa - reference", new LinkedHashMap<>()));
            fail("Expected BadRequestError for a guidance picture with no details");
        } catch (BadRequestError e) {
            assertEquals("Guidance details are missing or invalid: position (a whole number, 1 or more), "
                    + "site name (a non-empty string), picture type (Reference photo or Framing outline).", e.getMessage());
        }
        verify(downloadableContentRepository, never()).save(any(DownloadableContent.class));
    }

    // A record saved with bad details through the API can still be voided.
    @Test
    public void voidingAGuidancePictureWithBadDetailsIsAllowed() {
        DownloadableContentRequest request = guidanceRequest(null, "QA dup text position", "91", "reckoner");
        request.setVoided(true);

        assertTrue(service.createOrUpdate(request).isVoided());
    }

    private DownloadableContent guidanceRecord(String uuid, String name, Object sequence, Object kind) {
        DownloadableContent content = new DownloadableContent();
        content.setUuid(uuid);
        content.setName(name);
        content.setCategory("guidanceImage");
        content.setPayload(new JsonObject(guidancePayload(sequence, kind)));
        return content;
    }

    private DownloadableContentRequest guidanceRequest(String uuid, String name, Object sequence, Object kind) {
        return guidanceRequest(uuid, name, guidancePayload(sequence, kind));
    }

    private DownloadableContentRequest guidanceRequest(String uuid, String name, Map<String, Object> payload) {
        String sha256 = String.join("", Collections.nCopies(64, "a"));
        DownloadableContentRequest request = new DownloadableContentRequest();
        request.setUuid(uuid);
        request.setName(name);
        request.setCategory("guidanceImage");
        request.setContentKey("guidance/" + sha256 + ".png");
        request.setSha256(sha256);
        request.setNeedsKey(false);
        request.setPayload(payload);
        return request;
    }

    private Map<String, Object> guidancePayload(Object sequence, Object kind) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sequence", sequence);
        payload.put("site", "Left buccal mucosa");
        payload.put("kind", kind);
        return payload;
    }

    private void assertGuidanceRefused(DownloadableContentRequest request, String expectedProblem) {
        try {
            service.createOrUpdate(request);
            fail("Expected BadRequestError naming " + expectedProblem);
        } catch (BadRequestError e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedProblem));
        }
        verify(downloadableContentRepository, never()).save(any(DownloadableContent.class));
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
