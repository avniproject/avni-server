package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
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
}
