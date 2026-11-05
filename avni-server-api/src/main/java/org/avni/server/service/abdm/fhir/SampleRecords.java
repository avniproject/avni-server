package org.avni.server.service.abdm.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmTime;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// DEMO-ONLY abdm-hackathon: never merged.
// M3 test data. Avni's clinic side (HIP) serves one sample record of each ABDM record type other than OPConsultation,
// so NHA's HIU cases HIU_FLOW_107 to 114 run end to end through Eka: consent, data request, push, display.
// Each template (resources abdm/samples/) is an NRCES example bundle from NHA's catalogue (abdm-docs
// fhir-example:<hiType>, fetched 5 Nov 2026), in two forms, each checked with NHA's validator for its record type:
// structured (the coded resources, no attachment) and "-document" (only the attached document, a one-page PDF marked
// test data). HealthDocumentRecord is a document by nature: an image and a PDF. Invoice has no document form. The
// narratives were removed, because they name the example's fictional patient.
// At build time the example's patient becomes the Avni subject, the Composition's custodian becomes this HIP, and the
// bundle gets a new id, identifier, timestamp and Composition date.
@Component
public class SampleRecords {
    // ABDM record type -> its templates. Ordered as NHA numbers the cases (HIU_FLOW_107 to 114, 110 being OPConsultation).
    public static final Map<String, List<String>> FORMS;
    static {
        Map<String, List<String>> forms = new LinkedHashMap<>();
        forms.put("DiagnosticReport", List.of("DiagnosticReport", "DiagnosticReport-document"));
        forms.put("Prescription", List.of("Prescription", "Prescription-document"));
        forms.put("DischargeSummary", List.of("DischargeSummary", "DischargeSummary-document"));
        forms.put("ImmunizationRecord", List.of("ImmunizationRecord", "ImmunizationRecord-document"));
        forms.put("HealthDocumentRecord", List.of("HealthDocumentRecord", "HealthDocumentRecord-document"));
        forms.put("WellnessRecord", List.of("WellnessRecord", "WellnessRecord-document"));
        forms.put("Invoice", List.of("Invoice"));
        FORMS = java.util.Collections.unmodifiableMap(forms);
    }

    private static final String PREFIX = "sample-";
    // Longest first, so "Prescription-document" is matched before "Prescription".
    private static final List<String> TEMPLATES = FORMS.values().stream().flatMap(List::stream)
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    private static final Map<String, ObjectNode> LOADED = new ConcurrentHashMap<>();

    private final FhirBundleBuilder builder;

    public SampleRecords(FhirBundleBuilder builder) {
        this.builder = builder;
    }

    // ---- references: a sample's care context names its template and its subject

    public static String reference(String template, String subjectUuid) {
        return PREFIX + template + "-" + subjectUuid;
    }

    public static boolean isSample(String reference) {
        return templateOf(reference) != null;
    }

    // The template a sample reference names, or null when it names none.
    public static String templateOf(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) return null;
        for (String t : TEMPLATES) {
            if (reference.startsWith(PREFIX + t + "-") && reference.length() > PREFIX.length() + t.length() + 1) return t;
        }
        return null;
    }

    public static String hiTypeOf(String template) {
        return template.endsWith("-document") ? template.substring(0, template.length() - "-document".length()) : template;
    }

    // What the patient sees for this record in their ABHA app: the template's title.
    public static String title(String template) {
        return template(template).at("/entry/0/resource/title").asText();
    }

    // ---- building

    public ObjectNode build(String template, CareContextDocument patient) {
        ObjectNode bundle = template(template).deepCopy();
        String now = AbdmTime.format(builder.now());
        bundle.put("id", builder.nextId());
        ObjectNode identifier = bundle.get("identifier") instanceof ObjectNode o ? o : bundle.putObject("identifier");
        identifier.put("value", builder.nextId());
        bundle.put("timestamp", now);
        ObjectNode composition = (ObjectNode) bundle.at("/entry/0/resource");
        composition.put("date", now);

        // Every template names this HIP as the record's custodian; one without a custodian gets one.
        String custodian = composition.at("/custodian/reference").asText(null);
        if (custodian == null) {
            String id = builder.nextId();
            custodian = "urn:uuid:" + id;
            ((ArrayNode) bundle.get("entry")).addObject().put("fullUrl", custodian).set("resource", builder.organisation(patient, id));
            composition.putObject("custodian").put("reference", custodian);
        }
        String patientUrl = null;
        for (JsonNode e : bundle.get("entry")) {
            ObjectNode entry = (ObjectNode) e;
            JsonNode resource = entry.get("resource");
            String type = resource.path("resourceType").asText();
            String id = resource.path("id").asText();
            if ("Patient".equals(type)) {
                patientUrl = entry.path("fullUrl").asText();
                entry.set("resource", builder.patient(patient, id));
            } else if ("Organization".equals(type) && entry.path("fullUrl").asText().equals(custodian)) {
                entry.set("resource", builder.organisation(patient, id));
            }
        }
        // A reference to the patient keeps no display: the example's carries its own patient's name.
        if (patientUrl != null) dropDisplay(bundle, patientUrl);
        return bundle;
    }

    private static void dropDisplay(JsonNode node, String reference) {
        if (node instanceof ObjectNode o) {
            if (reference.equals(o.path("reference").asText(null))) o.remove("display");
            o.elements().forEachRemaining(c -> dropDisplay(c, reference));
        } else if (node instanceof ArrayNode a) {
            a.forEach(c -> dropDisplay(c, reference));
        }
    }

    private static ObjectNode template(String template) {
        if (!TEMPLATES.contains(template)) {
            throw new IllegalArgumentException("No sample record template " + template);
        }
        return LOADED.computeIfAbsent(template, t -> {
            try (InputStream in = SampleRecords.class.getResourceAsStream("/abdm/samples/" + t + ".json")) {
                if (in == null) throw new IllegalStateException("Sample record template missing from the build: " + t);
                JsonNode read = AbdmJson.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (!(read instanceof ObjectNode o) || !(o.get("entry") instanceof ArrayNode)) {
                    throw new IllegalStateException("Sample record template is not a bundle: " + t);
                }
                return o;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
