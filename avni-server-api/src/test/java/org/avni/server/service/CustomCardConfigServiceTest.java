package org.avni.server.service;

import org.avni.server.dao.CardRepository;
import org.avni.server.dao.CustomCardConfigRepository;
import org.avni.server.dao.TranslationRepository;
import org.avni.server.domain.CustomCardConfig;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Locale;
import org.avni.server.domain.Translation;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.CustomCardConfigRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CustomCardConfigServiceTest {

    @Mock
    private CustomCardConfigRepository customCardConfigRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private TranslationRepository translationRepository;

    private CustomCardConfigService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new CustomCardConfigService(customCardConfigRepository, cardRepository, s3Service, translationRepository);
        when(customCardConfigRepository.save(any(CustomCardConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customCardConfigRepository.findAllByIsVoidedFalse()).thenReturn(Collections.emptyList());
        when(translationRepository.findByLanguage(Locale.en)).thenReturn(null);
    }

    @Test
    public void applyTranslationsTrimsAndDeduplicates() {
        Map<String, String> requested = new LinkedHashMap<>();
        requested.put(" foo ", "x");
        requested.put("foo", "y");
        requested.put("bar", "b");

        CustomCardConfig saved = service.createOrUpdateCustomCardConfig(buildRequest("Card A", requested));

        JsonObject persisted = saved.getTranslations();
        assertEquals(2, persisted.size());
        assertEquals("x", persisted.get("foo"));
        assertEquals("b", persisted.get("bar"));
    }

    @Test
    public void applyTranslationsRejectsKeyOwnedByOtherCard() {
        CustomCardConfig other = new CustomCardConfig();
        other.assignUUID();
        other.setName("Card A");
        JsonObject otherTranslations = new JsonObject();
        otherTranslations.put("k", "v");
        other.setTranslations(otherTranslations);
        when(customCardConfigRepository.findAllByIsVoidedFalse()).thenReturn(List.of(other));

        Map<String, String> requested = new LinkedHashMap<>();
        requested.put("k", "w");

        try {
            service.createOrUpdateCustomCardConfig(buildRequest("Card B", requested));
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("'k'"));
            assertTrue(e.getMessage().contains("Card A"));
        }
        verify(customCardConfigRepository, times(0)).save(any(CustomCardConfig.class));
    }

    @Test
    public void applyTranslationsAllowsSameKeyOnSelfReSave() {
        String existingUuid = "self-uuid";
        CustomCardConfig existing = new CustomCardConfig();
        existing.setUuid(existingUuid);
        existing.setName("Card A");
        JsonObject existingTranslations = new JsonObject();
        existingTranslations.put("k", "v");
        existing.setTranslations(existingTranslations);
        when(customCardConfigRepository.findByUuid(existingUuid)).thenReturn(existing);
        when(customCardConfigRepository.findAllByIsVoidedFalse()).thenReturn(List.of(existing));

        Map<String, String> requested = new LinkedHashMap<>();
        requested.put("k", "v");

        CustomCardConfigRequest request = buildRequest("Card A", requested);
        request.setUuid(existingUuid);
        CustomCardConfig saved = service.createOrUpdateCustomCardConfig(request);

        assertEquals("v", saved.getTranslations().get("k"));
    }

    @Test
    public void applyTranslationsRejectsKeyMismatchWithExistingEnglishTranslation() {
        Translation english = new Translation();
        english.setLanguage(Locale.en);
        JsonObject json = new JsonObject();
        json.put("k", "stale");
        english.setTranslationJson(json);
        when(translationRepository.findByLanguage(Locale.en)).thenReturn(english);

        Map<String, String> requested = new LinkedHashMap<>();
        requested.put("k", "fresh");

        try {
            service.createOrUpdateCustomCardConfig(buildRequest("Card A", requested));
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("'k'"));
            assertTrue(e.getMessage().contains("stale"));
        }
        verify(customCardConfigRepository, times(0)).save(any(CustomCardConfig.class));
    }

    @Test
    public void applyTranslationsAllowsKeyMatchingExistingEnglishTranslation() {
        Translation english = new Translation();
        english.setLanguage(Locale.en);
        JsonObject json = new JsonObject();
        json.put("k", "v");
        english.setTranslationJson(json);
        when(translationRepository.findByLanguage(Locale.en)).thenReturn(english);

        Map<String, String> requested = new LinkedHashMap<>();
        requested.put("k", "v");

        CustomCardConfig saved = service.createOrUpdateCustomCardConfig(buildRequest("Card A", requested));
        assertEquals("v", saved.getTranslations().get("k"));
    }

    @Test
    public void applyTranslationsHandlesNullTranslations() {
        CustomCardConfig saved = service.createOrUpdateCustomCardConfig(buildRequest("Card A", null));
        assertNull(saved.getTranslations());
    }

    private CustomCardConfigRequest buildRequest(String name, Map<String, String> translations) {
        CustomCardConfigRequest request = new CustomCardConfigRequest();
        request.setName(name);
        request.setTranslations(translations);
        return request;
    }
}
