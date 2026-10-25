package org.avni.server.service.abdm.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmJson;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class FhirBundleBuilderTest {
    private static final String GOLDEN = "/abdm/fhir/op-consultation-sample.json";
    private static final Instant VISIT = Instant.parse("2026-10-20T04:45:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T05:30:00Z"), ZoneOffset.UTC);

    private static Supplier<String> sequentialIds() {
        AtomicInteger n = new AtomicInteger();
        return () -> String.format("7b3e9c1a-5d2f-4a8b-9c0d-%012d", n.incrementAndGet());
    }

    private static FhirBundleBuilder builder() {
        return new FhirBundleBuilder(sequentialIds(), CLOCK);
    }

    private static CareContextDocument doc(String abhaNumber, String abhaAddress, CareContextDocument.Item... items) {
        return new CareContextDocument("2f0c6a1e-8b7d-4c3e-a5f9-1d2e3f4a5b6c", "Hypertension Screening", VISIT,
                "c8a1d2e3-4f5a-4b6c-8d7e-9f0a1b2c3d4e", "Lakshmi Devi", "Female", new LocalDate(1968, 3, 12),
                abhaNumber, abhaAddress, "Kavya R", "TANUH", "TANUH Health Centre", Arrays.asList(items));
    }

    static CareContextDocument tanuhSample() {
        return doc("91-5555-0000-0042", "lakshmi.devi@sbx",
                new CareContextDocument.Item("Systolic blood pressure", "Numeric", 130, "mmHg"),
                new CareContextDocument.Item("Diastolic blood pressure", "Numeric", 85, "mmHg"),
                new CareContextDocument.Item("Tobacco use", "Coded", "Yes", null));
    }

    private static List<JsonNode> resources(JsonNode bundle, String type) {
        List<JsonNode> out = new ArrayList<>();
        bundle.get("entry").forEach(e -> {
            if (type.equals(e.get("resource").get("resourceType").asText())) out.add(e.get("resource"));
        });
        return out;
    }

    private static JsonNode onlyObservation(CareContextDocument.Item item) {
        List<JsonNode> obs = resources(builder().build(doc(null, null, item)), "Observation");
        assertEquals(1, obs.size());
        return obs.get(0);
    }

    private static void walk(JsonNode node, String path, List<String> nulls, List<String> references) {
        if (node.isNull() || node.isMissingNode()) {
            nulls.add(path);
        } else if (node.isObject()) {
            node.fields().forEachRemaining(f -> {
                if (f.getKey().equals("reference")) references.add(f.getValue().asText());
                walk(f.getValue(), path + "." + f.getKey(), nulls, references);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) walk(node.get(i), path + "[" + i + "]", nulls, references);
        }
    }

    @Test
    public void bundleIsAnNrcesDocumentBundle() {
        ObjectNode bundle = builder().build(tanuhSample());

        assertEquals("Bundle", bundle.get("resourceType").asText());
        assertEquals("document", bundle.get("type").asText());
        assertEquals("https://nrces.in/ndhm/fhir/r4/StructureDefinition/DocumentBundle", bundle.at("/meta/profile/0").asText());
        assertEquals("http://hip.in", bundle.at("/identifier/system").asText());
        assertEquals(bundle.get("id").asText(), bundle.at("/identifier/value").asText());
        assertEquals("2026-10-26T05:30:00.000Z", bundle.get("timestamp").asText());
    }

    @Test
    public void compositionIsTheFirstEntry() {
        JsonNode composition = builder().build(tanuhSample()).at("/entry/0/resource");

        assertEquals("Composition", composition.get("resourceType").asText());
        assertEquals("https://nrces.in/ndhm/fhir/r4/StructureDefinition/OPConsultRecord", composition.at("/meta/profile/0").asText());
        assertEquals("final", composition.get("status").asText());
        assertEquals("371530004", composition.at("/type/coding/0/code").asText());
        assertEquals("Hypertension Screening — TANUH Health Centre", composition.get("title").asText());
        assertEquals("2026-10-20T04:45:00.000Z", composition.get("date").asText());
        assertEquals(3, composition.at("/section/0/entry").size());
    }

    @Test
    public void everyReferenceResolvesToAnEntryFullUrl() {
        ObjectNode bundle = builder().build(tanuhSample());
        Set<String> fullUrls = new HashSet<>();
        bundle.get("entry").forEach(e -> {
            assertTrue(e.get("fullUrl").asText().startsWith("urn:uuid:"));
            assertEquals("urn:uuid:" + e.at("/resource/id").asText(), e.get("fullUrl").asText());
            fullUrls.add(e.get("fullUrl").asText());
        });
        List<String> references = new ArrayList<>();
        walk(bundle, "$", new ArrayList<>(), references);

        assertTrue(references.size() >= 8);
        for (String reference : references) assertTrue(reference, fullUrls.contains(reference));
        assertEquals(bundle.get("entry").size(), fullUrls.size());
    }

    @Test
    public void noNullValuesAnywhere() {
        CareContextDocument sparse = new CareContextDocument(null, null, null, null, null, null, null, null, null, null, null, null,
                Arrays.asList(new CareContextDocument.Item("Remarks", "Text", null, null),
                        new CareContextDocument.Item(null, "Text", "x", null),
                        new CareContextDocument.Item("Weight", "Numeric", 61.5, null)));
        List<String> nulls = new ArrayList<>();
        walk(builder().build(sparse), "$", nulls, new ArrayList<>());
        walk(builder().build(tanuhSample()), "$", nulls, new ArrayList<>());

        assertEquals(Collections.emptyList(), nulls);
    }

    @Test
    public void numericBecomesAQuantity() {
        JsonNode obs = onlyObservation(new CareContextDocument.Item("Systolic blood pressure", "Numeric", 130, "mmHg"));

        assertEquals("Systolic blood pressure", obs.at("/code/text").asText());
        assertEquals("final", obs.get("status").asText());
        assertEquals(130, obs.at("/valueQuantity/value").asInt());
        assertTrue(obs.at("/valueQuantity/value").isIntegralNumber());
        assertEquals("mmHg", obs.at("/valueQuantity/unit").asText());
        assertEquals("2026-10-20T04:45:00.000Z", obs.get("effectiveDateTime").asText());

        JsonNode decimal = onlyObservation(new CareContextDocument.Item("Temperature", "Numeric", "98.6", null));
        assertEquals(98.6, decimal.at("/valueQuantity/value").asDouble(), 0.0001);
        assertTrue(decimal.at("/valueQuantity/unit").isMissingNode());
    }

    @Test
    public void codedBecomesACodeableConceptText() {
        assertEquals("Yes", onlyObservation(new CareContextDocument.Item("Tobacco use", "Coded", "Yes", null)).at("/valueCodeableConcept/text").asText());
        assertEquals("Headache, Fever", onlyObservation(new CareContextDocument.Item("Symptoms", "Coded", Arrays.asList("Headache", "Fever"), null))
                .at("/valueCodeableConcept/text").asText());
    }

    @Test
    public void datesBecomeValueDateTime() {
        assertEquals("2026-09-01", onlyObservation(new CareContextDocument.Item("LMP", "Date", new LocalDate(2026, 9, 1), null)).get("valueDateTime").asText());
        assertEquals("2026-09-01", onlyObservation(new CareContextDocument.Item("LMP", "Date", "2026-09-01", null)).get("valueDateTime").asText());
        assertEquals("2026-10-19T10:15:00.000Z", onlyObservation(new CareContextDocument.Item("Last dose", "DateTime", Instant.parse("2026-10-19T10:15:00Z"), null))
                .get("valueDateTime").asText());
    }

    @Test
    public void textBecomesAString() {
        assertEquals("Advised low salt diet", onlyObservation(new CareContextDocument.Item("Advice", "Notes", "Advised low salt diet", null)).get("valueString").asText());
    }

    @Test
    public void mediaAndReferenceTypesAreNotShared() {
        CareContextDocument d = doc(null, null,
                new CareContextDocument.Item("Photo", "Image", "a.jpg", null),
                new CareContextDocument.Item("Photo 2", "ImageV2", "[]", null),
                new CareContextDocument.Item("Video", "Video", "v.mp4", null),
                new CareContextDocument.Item("Audio", "Audio", "a.mp3", null),
                new CareContextDocument.Item("File", "File", "f.pdf", null),
                new CareContextDocument.Item("Mother", "Subject", "uuid", null),
                new CareContextDocument.Item("Village", "Location", "uuid", null),
                new CareContextDocument.Item("Card number", "Id", "X1", null),
                new CareContextDocument.Item("Members", "QuestionGroup", Collections.singletonMap("a", "b"), null),
                new CareContextDocument.Item("Pulse", "Numeric", 72, "bpm"));
        ObjectNode bundle = builder().build(d);

        List<JsonNode> obs = resources(bundle, "Observation");
        assertEquals(1, obs.size());
        assertEquals("Pulse", obs.get(0).at("/code/text").asText());
        assertEquals(1, bundle.at("/entry/0/resource/section/0/entry").size());
    }

    @Test
    public void patientCarriesAbhaIdentifiersOnlyWhenGiven() {
        JsonNode patient = resources(builder().build(tanuhSample()), "Patient").get(0);
        assertEquals(2, patient.get("identifier").size());
        assertEquals("https://healthid.ndhm.gov.in", patient.at("/identifier/0/system").asText());
        assertEquals("ABHA Number", patient.at("/identifier/0/type/text").asText());
        assertEquals("91-5555-0000-0042", patient.at("/identifier/0/value").asText());
        assertEquals("ABHA Address", patient.at("/identifier/1/type/text").asText());
        assertEquals("lakshmi.devi@sbx", patient.at("/identifier/1/value").asText());
        assertEquals("Lakshmi Devi", patient.at("/name/0/text").asText());
        assertEquals("female", patient.get("gender").asText());
        assertEquals("1968-03-12", patient.get("birthDate").asText());

        JsonNode onlyAddress = resources(builder().build(doc(null, "lakshmi.devi@sbx")), "Patient").get(0);
        assertEquals(1, onlyAddress.get("identifier").size());
        assertEquals("ABHA Address", onlyAddress.at("/identifier/0/type/text").asText());

        assertFalse(resources(builder().build(doc(null, null)), "Patient").get(0).has("identifier"));
    }

    @Test
    public void genderMapsToFhirCodes() {
        assertEquals("male", FhirBundleBuilder.fhirGender("M"));
        assertEquals("male", FhirBundleBuilder.fhirGender("Male"));
        assertEquals("female", FhirBundleBuilder.fhirGender("F"));
        assertEquals("other", FhirBundleBuilder.fhirGender("Other"));
        assertEquals("unknown", FhirBundleBuilder.fhirGender(null));
        assertEquals("unknown", FhirBundleBuilder.fhirGender("Transgender"));
    }

    @Test
    public void practitionerOrganisationAndEncounter() {
        ObjectNode bundle = builder().build(tanuhSample());
        assertEquals("Kavya R", resources(bundle, "Practitioner").get(0).at("/name/0/text").asText());
        JsonNode org = resources(bundle, "Organization").get(0);
        assertEquals("https://facility.ndhm.gov.in", org.at("/identifier/0/system").asText());
        assertEquals("TANUH", org.at("/identifier/0/value").asText());
        assertEquals("TANUH Health Centre", org.get("name").asText());
        JsonNode encounter = resources(bundle, "Encounter").get(0);
        assertEquals("finished", encounter.get("status").asText());
        assertEquals("AMB", encounter.at("/class/code").asText());
        assertEquals("2026-10-20T04:45:00.000Z", encounter.at("/period/start").asText());

        CareContextDocument noPractitioner = new CareContextDocument(null, "Hypertension Screening", VISIT, null, "A", "M", null, null, null, null,
                "TANUH", "TANUH Health Centre", List.of());
        assertEquals("Health worker", resources(builder().build(noPractitioner), "Practitioner").get(0).at("/name/0/text").asText());
    }

    @Test
    public void defaultConstructorGivesFreshUuids() {
        FhirBundleBuilder b = new FhirBundleBuilder();
        String first = b.build(tanuhSample()).get("id").asText();
        assertNotEquals(first, b.build(tanuhSample()).get("id").asText());
        assertEquals(36, first.length());
    }

    // Regenerate with: ABDM_WRITE_GOLDEN=1 ./gradlew :avni-server-api:test --tests '*FhirBundleBuilderTest' --offline
    @Test
    public void tanuhSampleMatchesTheGoldenFile() throws Exception {
        ObjectNode built = builder().build(tanuhSample());
        if (System.getenv("ABDM_WRITE_GOLDEN") != null) {
            Path path = Path.of(System.getProperty("user.dir"), "src/test/resources" + GOLDEN);
            Files.createDirectories(path.getParent());
            Files.writeString(path, AbdmJson.M.writerWithDefaultPrettyPrinter().writeValueAsString(built) + "\n", StandardCharsets.UTF_8);
        }
        try (InputStream in = getClass().getResourceAsStream(GOLDEN)) {
            assertNotNull("golden sample missing: " + GOLDEN, in);
            // Through text, so an int read back equals the long that was built.
            assertEquals(AbdmJson.M.readTree(in), AbdmJson.read(AbdmJson.write(built)));
        }
    }
}
