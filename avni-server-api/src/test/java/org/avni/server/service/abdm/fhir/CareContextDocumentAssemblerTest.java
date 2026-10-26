package org.avni.server.service.abdm.fhir;

import org.avni.server.application.*;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.avni.server.service.FormMappingService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: never merged.
public class CareContextDocumentAssemblerTest {
    private ConceptRepository conceptRepository;
    private FormMappingService formMappingService;
    private CareContextDocumentAssembler assembler;
    private Concept systolic, diastolic, tobacco, symptoms, lmp, photo, weight;
    private Encounter encounter;
    private Individual individual;

    private Concept concept(String name, String dataType, String uuid, String unit) {
        Concept c = Concept.create(name, dataType, uuid);
        c.setUnit(unit);
        when(conceptRepository.findByUuid(uuid)).thenReturn(c);
        return c;
    }

    private static FormElement element(Concept c, double order) {
        return new TestFormElementBuilder().withConcept(c).withDisplayOrder(order).withUuid("fe-" + c.getUuid()).build();
    }

    // AVNI-08: a question's standard code comes from its concept's key-values, set by the implementer.
    @Test
    public void aConceptsStandardCodesTravelWithItsAnswer() {
        org.avni.server.application.KeyValues keyValues = new org.avni.server.application.KeyValues();
        keyValues.add(new org.avni.server.application.KeyValue("snomedCode", "271649006"));
        keyValues.add(new org.avni.server.application.KeyValue("loincCode", "8480-6"));
        systolic.setKeyValues(keyValues);

        CareContextDocument d = assembler.assemble(encounter, individual, null, null, "TANUH", "TANUH Health Centre");

        CareContextDocument.Item item = d.items().stream().filter(i -> i.name().equals("Systolic blood pressure")).findFirst().orElseThrow();
        assertEquals(java.util.List.of(new CareContextDocument.Code("http://loinc.org", "8480-6"),
                new CareContextDocument.Code("http://snomed.info/sct", "271649006")), item.codes());
    }

    @Before
    public void setUp() {
        conceptRepository = mock(ConceptRepository.class);
        formMappingService = mock(FormMappingService.class);
        assembler = new CareContextDocumentAssembler(conceptRepository, formMappingService);

        systolic = concept("Systolic blood pressure", "Numeric", "c-sys", "mmHg");
        diastolic = concept("Diastolic blood pressure", "Numeric", "c-dia", "mmHg");
        tobacco = concept("Tobacco use", "Coded", "c-tob", null);
        symptoms = concept("Symptoms", "Coded", "c-sym", null);
        lmp = concept("Last menstrual period", "Date", "c-lmp", null);
        photo = concept("Photo", "Image", "c-photo", null);
        weight = concept("Weight", "Numeric", "c-wt", "kg");
        concept("Yes", "NA", "a-yes", null);
        concept("Headache", "NA", "a-headache", null);
        concept("Fever", "NA", "a-fever", null);

        EncounterType type = EncounterType.create("Hypertension Screening");
        type.setUuid("et-1");
        User asha = new User();
        asha.setName("Kavya R");
        encounter = new Encounter();
        encounter.setUuid("enc-1");
        encounter.setEncounterType(type);
        encounter.setEncounterDateTime(new DateTime(2026, 10, 20, 10, 15, DateTimeZone.forID("Asia/Kolkata")), asha);
        ObservationCollection observations = new ObservationCollection();
        observations.put("c-wt", 61.5);
        observations.put("c-dia", 85);
        observations.put("c-tob", "a-yes");
        observations.put("c-sys", 130);
        observations.put("c-sym", Arrays.asList("a-headache", "a-fever", "a-unknown"));
        observations.put("c-lmp", "2026-08-31T18:30:00.000Z");
        observations.put("c-photo", "photo.jpg");
        observations.put("c-gone", "orphan");
        encounter.setObservations(observations);

        individual = new Individual();
        individual.setUuid("ind-1");
        individual.setFirstName("Lakshmi");
        individual.setLastName("Devi");
        individual.setGender(Gender.create("Female"));
        individual.setDateOfBirth(new LocalDate(1968, 3, 12));

        Form form = new TestFormBuilder().withName("Hypertension Screening").withFormType(FormType.Encounter)
                .addFormElementGroup(new TestFormElementGroupBuilder().withDisplayOrder(1.0)
                        .addFormElement(element(systolic, 1), element(diastolic, 2), element(tobacco, 3), element(symptoms, 4)).build())
                .build();
        FormMapping mapping = new FormMapping();
        mapping.setForm(form);
        when(formMappingService.find(type, FormType.Encounter)).thenReturn(mapping);
    }

    private CareContextDocument assemble() {
        return assembler.assemble(encounter, individual, "91-5555-0000-0042", "lakshmi.devi@sbx", "TANUH", "TANUH Health Centre");
    }

    private static CareContextDocument.Item item(CareContextDocument d, String name) {
        return d.items().stream().filter(i -> i.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    public void carriesThePatientTheVisitAndTheHip() {
        CareContextDocument d = assemble();

        assertEquals("enc-1", d.encounterUuid());
        assertEquals("Hypertension Screening", d.encounterTypeName());
        assertEquals(Instant.parse("2026-10-20T04:45:00Z"), d.encounterDateTime());
        assertEquals("ind-1", d.patientUuid());
        assertEquals("Lakshmi Devi", d.patientName());
        assertEquals("Female", d.gender());
        assertEquals(new LocalDate(1968, 3, 12), d.dateOfBirth());
        assertEquals("91-5555-0000-0042", d.abhaNumber());
        assertEquals("lakshmi.devi@sbx", d.abhaAddress());
        assertEquals("Kavya R", d.practitionerName());
        assertEquals("TANUH", d.hipId());
        assertEquals("TANUH Health Centre", d.hipName());
    }

    @Test
    public void answersCarryTheirConceptTypeAndUnit() {
        CareContextDocument d = assemble();

        CareContextDocument.Item sys = item(d, "Systolic blood pressure");
        assertEquals("Numeric", sys.dataType());
        assertEquals(130, sys.value());
        assertEquals("mmHg", sys.unit());
        assertEquals("2026-08-31T18:30:00.000Z", item(d, "Last menstrual period").value());
        assertEquals("Image", item(d, "Photo").dataType());
    }

    @Test
    public void codedAnswersBecomeAnswerNames() {
        CareContextDocument d = assemble();

        assertEquals("Yes", item(d, "Tobacco use").value());
        assertEquals(Arrays.asList("Headache", "Fever"), item(d, "Symptoms").value());
    }

    @Test
    public void answersFollowTheFormThenName() {
        List<String> names = assemble().items().stream().map(CareContextDocument.Item::name).collect(Collectors.toList());

        assertEquals(Arrays.asList("Systolic blood pressure", "Diastolic blood pressure", "Tobacco use", "Symptoms",
                "Last menstrual period", "Photo", "Weight"), names);
    }

    @Test
    public void withoutAFormAnswersAreInNameOrder() {
        when(formMappingService.find(any(EncounterType.class), any(FormType.class))).thenThrow(new RuntimeException("no mapping"));

        List<String> names = assemble().items().stream().map(CareContextDocument.Item::name).collect(Collectors.toList());

        assertEquals(Arrays.asList("Diastolic blood pressure", "Last menstrual period", "Photo", "Symptoms", "Systolic blood pressure",
                "Tobacco use", "Weight"), names);
    }

    @Test
    public void practitionerFallsBackToLastModifiedByAndThenToNothing() {
        encounter.setFilledBy(null);
        User editor = new User();
        editor.setName("Dr Meera");
        encounter.setLastModifiedBy(editor);
        assertEquals("Dr Meera", assemble().practitionerName());

        encounter.setLastModifiedBy(null);
        assertNull(assemble().practitionerName());
    }

    @Test
    public void builtBundleFromAssembledDocumentHasOneObservationPerSharedAnswer() {
        when(conceptRepository.findByUuid(anyString())).thenAnswer(inv -> {
            String uuid = inv.getArgument(0);
            return Arrays.asList(systolic, diastolic, tobacco, symptoms, lmp, photo, weight).stream()
                    .filter(c -> c.getUuid().equals(uuid)).findFirst()
                    .orElseGet(() -> uuid.startsWith("a-") && !uuid.equals("a-unknown") ? Concept.create(uuid.substring(2), "NA", uuid) : null);
        });
        long observations = 0;
        for (var e : new FhirBundleBuilder().build(assemble()).get("entry")) {
            if (e.at("/resource/resourceType").asText().equals("Observation")) observations++;
        }
        // Photo is not shared; the orphan answer has no concept.
        assertEquals(6, observations);
    }
}
