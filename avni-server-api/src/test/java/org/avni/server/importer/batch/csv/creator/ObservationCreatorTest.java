package org.avni.server.importer.batch.csv.creator;

import org.avni.server.application.FormElement;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.service.EnhancedValidationService;
import org.avni.server.service.IndividualService;
import org.avni.server.service.LocationService;
import org.avni.server.service.ObservationService;
import org.avni.server.service.S3Service;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class ObservationCreatorTest {

    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private FormRepository formRepository;
    @Mock
    private ObservationService observationService;
    @Mock
    private S3Service s3Service;
    @Mock
    private IndividualService individualService;
    @Mock
    private LocationService locationService;
    @Mock
    private FormElementRepository formElementRepository;
    @Mock
    private EnhancedValidationService enhancedValidationService;

    private ObservationCreator observationCreator;

    @Before
    public void setup() {
        openMocks(this);
        observationCreator = new ObservationCreator(
                conceptRepository,
                formRepository,
                observationService,
                s3Service,
                individualService,
                locationService,
                formElementRepository,
                enhancedValidationService
        );
    }

    @Test
    public void handleDateValue_shouldReturnNullForEmptyString() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "", errorMsgs, concept);

        assertNull(result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDateValue_shouldReturnNullForWhitespaceString() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "   ", errorMsgs, concept);

        assertNull(result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDateValue_shouldConvertDateInDDMMYYYYFormat() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "25-02-2026", errorMsgs, concept);

        assertEquals("2026-02-24T18:30:00.000Z", result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDateValue_shouldConvertDateInYYYYMMDDFormat() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "2026-02-25", errorMsgs, concept);

        assertEquals("2026-02-24T18:30:00.000Z", result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDateValue_shouldConvertDateToMidnightIST() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "2025-12-24", errorMsgs, concept);

        assertEquals("2025-12-23T18:30:00.000Z", result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDateValue_shouldAddErrorMessageForInvalidDate() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "invalid-date", errorMsgs, concept);

        assertNull(result);
        assertEquals(1, errorMsgs.size());
        assertEquals("Invalid value 'invalid-date' for 'DateOfBirth'", errorMsgs.get(0));
    }

    @Test
    public void handleDateValue_shouldAddErrorMessageForInvalidDateFormat() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "2026/02/25", errorMsgs, concept);

        assertNull(result);
        assertEquals(1, errorMsgs.size());
        assertEquals("Invalid value '2026/02/25' for 'DateOfBirth'", errorMsgs.get(0));
    }

    @Test
    public void handleDateValue_shouldTrimWhitespace() throws Exception {
        Concept concept = new Concept();
        concept.setName("DateOfBirth");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleDateValue", String.class, List.class, Concept.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, "  2026-02-25  ", errorMsgs, concept);

        assertEquals("2026-02-24T18:30:00.000Z", result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleMediaV2Value_shouldReturnSingleElementJsonArrayForOneUrl() throws Exception {
        FormElement formElement = imageV2FormElement();
        when(s3Service.getObservationValueForUpload("https://example.com/a.png", null))
                .thenReturn("https://bucket.s3.amazonaws.com/org/a.png");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleMediaV2Value", FormElement.class, String.class, List.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, formElement, "https://example.com/a.png", errorMsgs);

        assertEquals("[{\"uri\":\"https://bucket.s3.amazonaws.com/org/a.png\"}]", result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleMediaV2Value_shouldReturnMultiElementJsonArrayForCommaSeparatedUrls() throws Exception {
        FormElement formElement = imageV2FormElement();
        when(s3Service.getObservationValueForUpload("https://example.com/a.png", null))
                .thenReturn("https://bucket.s3.amazonaws.com/org/a.png");
        when(s3Service.getObservationValueForUpload("https://example.com/b.png", null))
                .thenReturn("https://bucket.s3.amazonaws.com/org/b.png");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleMediaV2Value", FormElement.class, String.class, List.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, formElement, "https://example.com/a.png,https://example.com/b.png", errorMsgs);

        assertEquals("[{\"uri\":\"https://bucket.s3.amazonaws.com/org/a.png\"},{\"uri\":\"https://bucket.s3.amazonaws.com/org/b.png\"}]", result);
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleMediaV2Value_shouldExcludeFailedDownloadAndRecordError() throws Exception {
        FormElement formElement = imageV2FormElement();
        when(s3Service.getObservationValueForUpload("https://example.com/a.png", null))
                .thenThrow(new RuntimeException("download timed out"));
        when(s3Service.getObservationValueForUpload("https://example.com/b.png", null))
                .thenReturn("https://bucket.s3.amazonaws.com/org/b.png");
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleMediaV2Value", FormElement.class, String.class, List.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, formElement, "https://example.com/a.png,https://example.com/b.png", errorMsgs);

        assertEquals("[{\"uri\":\"https://bucket.s3.amazonaws.com/org/b.png\"}]", result);
        assertEquals(1, errorMsgs.size());
        assertTrue("error message should propagate from S3 failure: " + errorMsgs.get(0),
                errorMsgs.get(0).contains("download timed out"));
    }

    @Test
    public void handleMediaV2Value_shouldReturnEmptyJsonArrayWhenAllDownloadsFail() throws Exception {
        FormElement formElement = imageV2FormElement();
        when(s3Service.getObservationValueForUpload("https://example.com/a.png", null))
                .thenThrow(new RuntimeException("boom"));
        List<String> errorMsgs = new ArrayList<>();

        Method method = ObservationCreator.class.getDeclaredMethod("handleMediaV2Value", FormElement.class, String.class, List.class);
        method.setAccessible(true);

        Object result = method.invoke(observationCreator, formElement, "https://example.com/a.png", errorMsgs);

        assertEquals("[]", result);
        assertEquals(1, errorMsgs.size());
    }

    // avniproject/avni-server#959: a duration answer is stored only in the form the app reads.

    @Test
    public void handleDurationValue_shouldKeepAValueAlreadyInTheFormTheAppWrites() throws Exception {
        List<String> errorMsgs = new ArrayList<>();

        assertEquals("PT35M", handleDurationValue(durationFormElement("minutes"), "PT35M", errorMsgs));
        assertEquals("P1Y6M", handleDurationValue(durationFormElement("years", "months"), "P1Y6M", errorMsgs));
        assertEquals("PT2H30M", handleDurationValue(durationFormElement("hours", "minutes"), "PT2H30M", errorMsgs));
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDurationValue_shouldConvertAPlainNumberWhenTheQuestionHasOneUnit() throws Exception {
        List<String> errorMsgs = new ArrayList<>();

        assertEquals("P2Y", handleDurationValue(durationFormElement("years"), "2", errorMsgs));
        assertEquals("P6M", handleDurationValue(durationFormElement("months"), "6", errorMsgs));
        assertEquals("P3W", handleDurationValue(durationFormElement("weeks"), "3", errorMsgs));
        assertEquals("P10D", handleDurationValue(durationFormElement("days"), "10", errorMsgs));
        assertEquals("PT4H", handleDurationValue(durationFormElement("hours"), "4", errorMsgs));
        assertEquals("PT35M", handleDurationValue(durationFormElement("minutes"), "35", errorMsgs));
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDurationValue_shouldStoreOnlyValuesThatPassTheDurationCheckUsedForTheApp() throws Exception {
        List<String> errorMsgs = new ArrayList<>();
        for (String unit : new String[]{"years", "months", "weeks", "days", "hours", "minutes"}) {
            String stored = (String) handleDurationValue(durationFormElement(unit), "35", errorMsgs);
            assertTrue(unit + " gave " + stored, stored.matches(org.avni.messaging.domain.Constants.DURATION_PATTERN));
        }
    }

    @Test
    public void handleDurationValue_shouldTrimAndDropLeadingZeros() throws Exception {
        List<String> errorMsgs = new ArrayList<>();

        assertEquals("PT35M", handleDurationValue(durationFormElement("minutes"), "  035 ", errorMsgs));
        assertEquals("PT35M", handleDurationValue(durationFormElement("minutes"), " PT35M ", errorMsgs));
        assertEquals(0, errorMsgs.size());
    }

    @Test
    public void handleDurationValue_shouldRejectAPlainNumberWhenTheQuestionHasSeveralUnits() throws Exception {
        List<String> errorMsgs = new ArrayList<>();

        Object result = handleDurationValue(durationFormElement("hours", "minutes"), "35", errorMsgs);

        assertNull(result);
        assertEquals(1, errorMsgs.size());
        assertEquals("Invalid value '35' for 'Session duration'. Enter a duration such as PT30M (30 minutes) or P1Y6M (1 year 6 months)", errorMsgs.get(0));
    }

    @Test
    public void handleDurationValue_shouldRejectAPlainNumberWhenTheQuestionHasNoUnit() throws Exception {
        List<String> errorMsgs = new ArrayList<>();

        Object result = handleDurationValue(durationFormElement(), "35", errorMsgs);

        assertNull(result);
        assertEquals(1, errorMsgs.size());
    }

    @Test
    public void handleDurationValue_shouldRejectTextThatIsNotADurationAndSayWhatToEnter() throws Exception {
        List<String> errorMsgs = new ArrayList<>();

        assertNull(handleDurationValue(durationFormElement("minutes"), "35 mins", errorMsgs));
        assertNull(handleDurationValue(durationFormElement("minutes"), "35.5", errorMsgs));
        assertNull(handleDurationValue(durationFormElement("minutes"), "-35", errorMsgs));
        assertNull(handleDurationValue(durationFormElement("minutes"), "P", errorMsgs));
        assertNull(handleDurationValue(durationFormElement("minutes"), "99999999999999999999", errorMsgs));

        assertEquals(5, errorMsgs.size());
        assertEquals("Invalid value '35 mins' for 'Session duration'. Enter a whole number of minutes, or a duration such as PT30M (30 minutes) or P1Y6M (1 year 6 months)", errorMsgs.get(0));
    }

    @Test
    public void getObservationValue_shouldSendADurationQuestionThroughTheDurationHandling() throws Exception {
        List<String> errorMsgs = new ArrayList<>();
        Method method = ObservationCreator.class.getDeclaredMethod("getObservationValue", FormElement.class, String.class,
                org.avni.server.application.FormType.class, List.class, org.avni.server.importer.batch.model.Row.class,
                org.avni.server.importer.batch.csv.writer.header.HeaderCreator.class,
                org.avni.server.domain.ObservationCollection.class, boolean.class);
        method.setAccessible(true);

        Object converted = method.invoke(observationCreator, durationFormElement("minutes"), "35",
                org.avni.server.application.FormType.Encounter, errorMsgs, null, null, null, false);
        Object rejected = method.invoke(observationCreator, durationFormElement("hours", "minutes"), "35",
                org.avni.server.application.FormType.Encounter, errorMsgs, null, null, null, false);

        assertEquals("PT35M", converted);
        assertNull(rejected);
        assertEquals(1, errorMsgs.size());
    }

    private Object handleDurationValue(FormElement formElement, String answerValue, List<String> errorMsgs) throws Exception {
        Method method = ObservationCreator.class.getDeclaredMethod("handleDurationValue", FormElement.class, String.class, List.class, Concept.class);
        method.setAccessible(true);
        return method.invoke(observationCreator, formElement, answerValue, errorMsgs, formElement.getConcept());
    }

    private FormElement durationFormElement(String... durationOptions) {
        Concept concept = new ConceptBuilder().withName("Session duration").withUuid("duration-uuid").withDataType(ConceptDataType.Duration).build();
        TestFormElementBuilder builder = new TestFormElementBuilder().withConcept(concept);
        return durationOptions.length == 0 ? builder.build() : builder.withDurationOptions(durationOptions).build();
    }

    private FormElement imageV2FormElement() {
        Concept concept = new ConceptBuilder().withName("Photo").withUuid("photo-uuid").withDataType(ConceptDataType.ImageV2).build();
        return new TestFormElementBuilder().withConcept(concept).build();
    }
}
