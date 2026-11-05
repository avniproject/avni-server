package org.avni.server.service.abdm.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmJson;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class SampleRecordsTest {
    private static final Map<String, String> PROFILE = Map.of(
            "Prescription", "PrescriptionRecord", "DiagnosticReport", "DiagnosticReportRecord",
            "DischargeSummary", "DischargeSummaryRecord", "ImmunizationRecord", "ImmunizationRecord",
            "HealthDocumentRecord", "HealthDocumentRecord", "WellnessRecord", "WellnessRecord", "Invoice", "InvoiceRecord");
    private static final Instant NOW = Instant.parse("2026-11-06T05:30:00Z");

    private final AtomicInteger ids = new AtomicInteger();
    private final SampleRecords samples = new SampleRecords(
            new FhirBundleBuilder(() -> "id-" + ids.incrementAndGet(), Clock.fixed(NOW, ZoneOffset.UTC)));

    private static CareContextDocument testPatient() {
        return new CareContextDocument(null, null, null, "subject-1", "Test Patient", "Female", new LocalDate(1990, 4, 2),
                "91-1111-2222-3333", "test.patient@sbx", null, "TANUH", "TANUH Health Centre", List.of());
    }

    @Test
    public void everyRecordTypeHasItsFormsAndInvoiceHasNoDocument() {
        assertEquals(PROFILE.keySet(), SampleRecords.FORMS.keySet());
        SampleRecords.FORMS.forEach((type, forms) -> assertEquals(type, type.equals("Invoice") ? 1 : 2, forms.size()));
        assertEquals(List.of("Invoice"), SampleRecords.FORMS.get("Invoice"));
    }

    @Test
    public void eachSampleIsADocumentBundleOfItsTypeFilledWithTheAvniPatient() throws IOException {
        Path out = Path.of("build", "tmp", "abdm-samples");
        Files.createDirectories(out);
        for (Map.Entry<String, List<String>> e : SampleRecords.FORMS.entrySet()) {
            for (String template : e.getValue()) {
                ObjectNode b = samples.build(template, testPatient());
                Files.writeString(out.resolve(template + ".json"), AbdmJson.write(b));

                assertEquals(template, "Bundle", b.get("resourceType").asText());
                assertEquals(template, "document", b.get("type").asText());
                assertEquals(template, NOW, Instant.parse(b.get("timestamp").asText()));
                assertEquals(template, NOW, Instant.parse(b.at("/entry/0/resource/date").asText()));
                JsonNode composition = b.at("/entry/0/resource");
                assertEquals(template, "Composition", composition.get("resourceType").asText());
                assertEquals(template, "https://nrces.in/ndhm/fhir/r4/StructureDefinition/" + PROFILE.get(e.getKey()),
                        composition.at("/meta/profile/0").asText());
                assertTrue(template, composition.get("title").asText().endsWith("(test data)"));

                List<JsonNode> patients = resources(b, "Patient");
                assertEquals(template, 1, patients.size());
                JsonNode patient = patients.get(0);
                assertEquals(template, "Test Patient", patient.at("/name/0/text").asText());
                assertEquals(template, "female", patient.get("gender").asText());
                assertEquals(template, "1990-04-02", patient.get("birthDate").asText());
                assertTrue(template, AbdmJson.write(patient).contains("test.patient@sbx"));
                // The example's own patient leaves no trace: its id stays, so references still resolve, and no
                // reference to it still names the example's patient ("ABC" in NRCES's examples).
                assertFalse(template, patient.has("text"));
                assertFalse(template, AbdmJson.write(b).contains("\"ABC\""));

                assertEquals(template, referencesResolve(b), Set.of());
                boolean document = template.endsWith("-document") || template.equals("HealthDocumentRecord");
                assertEquals(template, document, hasAttachment(b));
            }
        }
    }

    @Test
    public void eachBuildIsANewDocument() {
        ObjectNode first = samples.build("Prescription", testPatient());
        ObjectNode second = samples.build("Prescription", testPatient());
        assertNotEquals(first.get("id").asText(), second.get("id").asText());
        assertNotEquals(first.at("/identifier/value").asText(), second.at("/identifier/value").asText());
    }

    @Test
    public void everySampleNamesTheHipAsItsCustodian() {
        // Four examples name a custodian, which becomes this HIP; the rest get this HIP as one.
        for (String template : SampleRecords.FORMS.values().stream().flatMap(List::stream).toList()) {
            ObjectNode b = samples.build(template, testPatient());
            String custodian = b.at("/entry/0/resource/custodian/reference").asText();
            JsonNode org = null;
            for (JsonNode en : b.get("entry")) if (custodian.equals(en.get("fullUrl").asText())) org = en.get("resource");
            assertNotNull(template, org);
            assertEquals(template, "Organization", org.get("resourceType").asText());
            assertEquals(template, "TANUH Health Centre", org.get("name").asText());
            assertEquals(template, "TANUH", org.at("/identifier/0/value").asText());
        }
    }

    @Test
    public void aReferenceNamesItsTemplateAndAnUnknownOneNamesNone() {
        String ref = SampleRecords.reference("Prescription-document", "0a1b2c3d-4e5f-6789-abcd-ef0123456789");
        assertEquals("sample-Prescription-document-0a1b2c3d-4e5f-6789-abcd-ef0123456789", ref);
        assertTrue(SampleRecords.isSample(ref));
        assertEquals("Prescription-document", SampleRecords.templateOf(ref));
        assertEquals("Prescription", SampleRecords.hiTypeOf("Prescription-document"));
        assertNull(SampleRecords.templateOf("sample-Nonsense-0a1b2c3d"));
        assertFalse(SampleRecords.isSample("6f1c2d3e-0000-4000-8000-000000000000"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnknownTemplateIsRefused() {
        samples.build("Nonsense", testPatient());
    }

    private static List<JsonNode> resources(JsonNode bundle, String type) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : bundle.get("entry")) if (type.equals(e.at("/resource/resourceType").asText())) out.add(e.get("resource"));
        return out;
    }

    private static Set<String> referencesResolve(JsonNode bundle) {
        Set<String> urls = new HashSet<>();
        for (JsonNode e : bundle.get("entry")) urls.add(e.get("fullUrl").asText());
        Set<String> missing = new HashSet<>();
        collect(bundle.get("entry"), urls, missing);
        return missing;
    }

    private static void collect(JsonNode n, Set<String> urls, Set<String> missing) {
        if (n == null) return;
        if (n.isObject()) {
            JsonNode r = n.get("reference");
            if (r != null && r.isTextual() && r.asText().startsWith("urn:uuid:") && !urls.contains(r.asText())) missing.add(r.asText());
            n.fields().forEachRemaining(f -> collect(f.getValue(), urls, missing));
        } else if (n.isArray()) {
            n.forEach(c -> collect(c, urls, missing));
        }
    }

    private static boolean hasAttachment(JsonNode n) {
        if (n == null) return false;
        if (n.isObject()) {
            if (n.has("contentType") && n.has("data") && !"Bundle".equals(n.path("resourceType").asText())) return true;
            var it = n.fields();
            while (it.hasNext()) {
                var f = it.next();
                if (!f.getKey().equals("signature") && hasAttachment(f.getValue())) return true;
            }
        } else if (n.isArray()) {
            for (JsonNode c : n) if (hasAttachment(c)) return true;
        }
        return false;
    }
}
