package org.avni.server.service.abdm.fhir;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

// DEMO-ONLY abdm-hackathon: never merged.
// A care context as an ABDM OPConsultation FHIR R4 document bundle, built with Jackson (no HAPI). Composition first,
// then Patient, Practitioner, Organization, Encounter, one Observation per answer, and a DocumentReference carrying the
// visit as a PDF. Never writes a null: a missing value leaves its field out. Checked against NRCES 6.5.0 with NHA's
// validator (docs.abdm.gov.in/mcp) through the golden sample.
@Component
public class FhirBundleBuilder {
    static final String NRCES = "https://nrces.in/ndhm/fhir/r4/StructureDefinition/";
    static final String SNOMED = "http://snomed.info/sct";
    static final String V2_0203 = "http://terminology.hl7.org/CodeSystem/v2-0203";
    static final String ABHA_SYSTEM = "https://healthid.ndhm.gov.in";
    static final String FACILITY_SYSTEM = "https://facility.ndhm.gov.in";
    // NRCES tells an ABHA number (HIN) from an ABHA address (ABHA) by this type code; it publishes no system for either.
    static final String NDHM_IDENTIFIER_TYPE = "https://nrces.in/ndhm/fhir/r4/CodeSystem/ndhm-identifier-type-code";
    private static final ZoneId AVNI_DATE_ZONE = ZoneId.of("Asia/Kolkata");
    // DEMO-ONLY: media, references to other records and nested groups are not shared.
    private static final Set<String> NOT_SHARED = Set.of("Image", "ImageV2", "Video", "Audio", "File", "Subject", "Location", "Id",
            "QuestionGroup", "Encounter", "GroupAffiliation");

    private final Supplier<String> idSupplier;
    private final Clock clock;

    public FhirBundleBuilder() {
        this(() -> UUID.randomUUID().toString(), Clock.systemUTC());
    }

    public FhirBundleBuilder(Supplier<String> idSupplier) {
        this(idSupplier, Clock.systemUTC());
    }

    public FhirBundleBuilder(Supplier<String> idSupplier, Clock clock) {
        this.idSupplier = idSupplier;
        this.clock = clock;
    }

    public ObjectNode build(CareContextDocument d) {
        String now = AbdmTime.format(clock.instant());
        String visitTime = d.encounterDateTime() == null ? now : AbdmTime.format(d.encounterDateTime());

        String bundleId = idSupplier.get();
        String compositionId = idSupplier.get();
        String patientId = idSupplier.get();
        String practitionerId = idSupplier.get();
        String organisationId = idSupplier.get();
        String encounterId = idSupplier.get();
        String documentId = idSupplier.get();

        List<ObjectNode> observations = new ArrayList<>();
        for (CareContextDocument.Item item : Optional.ofNullable(d.items()).orElse(List.of())) {
            ObjectNode observation = observation(item, patientId, encounterId, visitTime);
            if (observation != null) observations.add(observation);
        }

        ObjectNode bundle = AbdmJson.obj();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", bundleId);
        ObjectNode meta = bundle.putObject("meta");
        meta.put("versionId", "1");
        meta.put("lastUpdated", now);
        meta.putArray("profile").add(NRCES + "DocumentBundle");
        ObjectNode identifier = bundle.putObject("identifier");
        identifier.put("system", "http://hip.in");
        identifier.put("value", bundleId);
        bundle.put("type", "document");
        bundle.put("timestamp", now);

        ArrayNode entries = bundle.putArray("entry");
        entries.add(entry(composition(d, compositionId, patientId, practitionerId, organisationId, encounterId, documentId, visitTime, observations)));
        entries.add(entry(patient(d, patientId)));
        entries.add(entry(practitioner(d, practitionerId)));
        entries.add(entry(organisation(d, organisationId)));
        entries.add(entry(encounter(d, encounterId, patientId, visitTime)));
        observations.forEach(o -> entries.add(entry(o)));
        entries.add(entry(document(d, documentId, patientId, visitTime)));
        return bundle;
    }

    // The visit as a PDF, NHA's simple form ("a simple bundle wraps a PDF ... as an attachment"), beside the structured
    // record, as NRCES's OPConsultation samples carry one.
    private ObjectNode document(CareContextDocument d, String id, String patientId, String visitTime) {
        ObjectNode r = resource("DocumentReference", id, "DocumentReference");
        r.put("status", "current");
        r.put("docStatus", "final");
        r.set("type", codeable(SNOMED, "371530004", "Clinical consultation report"));
        r.set("subject", reference(patientId, d.patientName()));
        ObjectNode attachment = r.putArray("content").addObject().putObject("attachment");
        attachment.put("contentType", "application/pdf");
        attachment.put("language", "en-IN");
        attachment.put("data", Base64.getEncoder().encodeToString(SimplePdf.of(title(d), pdfLines(d, visitTime))));
        attachment.put("title", title(d));
        attachment.put("creation", visitTime);
        return r;
    }

    private static List<String> pdfLines(CareContextDocument d, String visitTime) {
        List<String> lines = new ArrayList<>();
        if (hasText(d.patientName())) lines.add("Patient: " + d.patientName());
        if (hasText(d.hipName())) lines.add("Facility: " + d.hipName());
        lines.add("Seen by: " + practitionerName(d));
        lines.add("Date: " + visitTime);
        lines.add("");
        for (CareContextDocument.Item item : Optional.ofNullable(d.items()).orElse(List.of())) {
            if (item == null || !hasText(item.name()) || item.value() == null) continue;
            if (NOT_SHARED.contains(item.dataType() == null ? "Text" : item.dataType())) continue;
            String value = item.value() instanceof Collection<?> c ? join(c) : item.value().toString();
            lines.add(item.name() + ": " + value + (hasText(item.unit()) ? " " + item.unit() : ""));
        }
        return lines;
    }

    private ObjectNode composition(CareContextDocument d, String id, String patientId, String practitionerId, String organisationId,
                                   String encounterId, String documentId, String visitTime, List<ObjectNode> observations) {
        ObjectNode c = resource("Composition", id, "OPConsultRecord");
        c.put("status", "final");
        c.set("type", codeable(SNOMED, "371530004", "Clinical consultation report"));
        c.set("subject", reference(patientId, d.patientName()));
        c.set("encounter", reference(encounterId, null));
        c.put("date", visitTime);
        c.putArray("author").add(reference(practitionerId, practitionerName(d)));
        c.put("title", title(d));
        c.set("custodian", reference(organisationId, d.hipName()));

        ObjectNode section = c.putArray("section").addObject();
        section.put("title", "Findings");
        // DEMO-ONLY coding: every answer goes under one physical-examination section, whatever the form asked.
        section.set("code", codeable(SNOMED, "425044008", "Physical exam section"));
        if (observations.isEmpty()) {
            section.set("emptyReason", codeable("http://terminology.hl7.org/CodeSystem/list-empty-reason", "nilknown", "Nil Known"));
        } else {
            ArrayNode refs = section.putArray("entry");
            observations.forEach(o -> refs.add(reference(o.get("id").asText(), null)));
        }
        ObjectNode documentSection = c.withArray("section").addObject();
        documentSection.put("title", "Document");
        documentSection.set("code", codeable(SNOMED, "371530004", "Clinical consultation report"));
        documentSection.putArray("entry").add(reference(documentId, "DocumentReference"));
        return c;
    }

    private ObjectNode patient(CareContextDocument d, String id) {
        ObjectNode p = resource("Patient", id, "Patient");
        ArrayNode identifiers = AbdmJson.arr();
        if (hasText(d.abhaNumber())) identifiers.add(abhaNumberIdentifier(d.abhaNumber()));
        if (hasText(d.abhaAddress())) identifiers.add(abhaAddressIdentifier(d.abhaAddress()));
        if (!identifiers.isEmpty()) p.set("identifier", identifiers);
        if (hasText(d.patientName())) p.putArray("name").addObject().put("text", d.patientName());
        p.put("gender", fhirGender(d.gender()));
        if (d.dateOfBirth() != null) p.put("birthDate", d.dateOfBirth().toString("yyyy-MM-dd"));
        return p;
    }

    private ObjectNode practitioner(CareContextDocument d, String id) {
        ObjectNode p = resource("Practitioner", id, "Practitioner");
        p.putArray("name").addObject().put("text", practitionerName(d));
        return p;
    }

    private ObjectNode organisation(CareContextDocument d, String id) {
        ObjectNode o = resource("Organization", id, "Organization");
        if (hasText(d.hipId())) {
            ObjectNode identifier = o.putArray("identifier").addObject();
            identifier.set("type", codeable(V2_0203, "PRN", "Provider number"));
            identifier.put("system", FACILITY_SYSTEM);
            identifier.put("value", d.hipId());
        }
        if (hasText(d.hipName())) o.put("name", d.hipName());
        return o;
    }

    private ObjectNode encounter(CareContextDocument d, String id, String patientId, String visitTime) {
        ObjectNode e = resource("Encounter", id, "Encounter");
        e.put("status", "finished");
        ObjectNode cls = e.putObject("class");
        cls.put("system", "http://terminology.hl7.org/CodeSystem/v3-ActCode");
        cls.put("code", "AMB");
        cls.put("display", "ambulatory");
        if (hasText(d.encounterTypeName())) e.putArray("type").addObject().put("text", d.encounterTypeName());
        e.set("subject", reference(patientId, null));
        e.putObject("period").put("start", visitTime);
        return e;
    }

    // Null when the answer is not shared or has no usable value.
    private ObjectNode observation(CareContextDocument.Item item, String patientId, String encounterId, String visitTime) {
        if (item == null || !hasText(item.name()) || item.value() == null) return null;
        String dataType = item.dataType() == null ? "Text" : item.dataType();
        if (NOT_SHARED.contains(dataType)) return null; // DEMO-ONLY: media not shared

        ObjectNode value = AbdmJson.obj();
        boolean hasValue = switch (dataType) {
            case "Numeric" -> putNumeric(value, item.value(), item.unit());
            case "Coded" -> putCoded(value, item.value());
            case "Date" -> putDate(value, item.value());
            case "DateTime" -> putDateTime(value, item.value());
            default -> putString(value, item.value());
        };
        if (!hasValue) return null;

        ObjectNode o = resource("Observation", idSupplier.get(), "Observation");
        o.put("status", "final");
        ObjectNode code = o.putObject("code");
        // The question's standard codes when its concept carries them (AVNI-08); its name always.
        if (item.codes() != null && !item.codes().isEmpty()) {
            ArrayNode codings = code.putArray("coding");
            for (CareContextDocument.Code c : item.codes()) {
                ObjectNode coding = codings.addObject();
                coding.put("system", c.system());
                coding.put("code", c.code());
                coding.put("display", item.name());
            }
        }
        code.put("text", item.name());
        o.set("subject", reference(patientId, null));
        o.set("encounter", reference(encounterId, null));
        o.put("effectiveDateTime", visitTime);
        o.setAll(value);
        return o;
    }

    private static boolean putNumeric(ObjectNode target, Object value, String unit) {
        BigDecimal number;
        try {
            number = value instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            return putString(target, value);
        }
        ObjectNode quantity = target.putObject("valueQuantity");
        if (number.signum() == 0 || number.stripTrailingZeros().scale() <= 0) {
            quantity.put("value", number.longValue());
        } else {
            quantity.put("value", number.doubleValue());
        }
        if (hasText(unit)) quantity.put("unit", unit);
        return true;
    }

    private static boolean putCoded(ObjectNode target, Object value) {
        String text;
        if (value instanceof Collection<?> c) {
            text = join(c);
        } else if (value instanceof Object[] a) {
            text = join(Arrays.asList(a));
        } else if (value instanceof Iterable<?> i) {
            text = join(StreamSupport.stream(i.spliterator(), false).collect(Collectors.toList()));
        } else if (value instanceof Map) {
            return false;
        } else {
            text = value.toString();
        }
        if (!hasText(text)) return false;
        target.putObject("valueCodeableConcept").put("text", text);
        return true;
    }

    private static boolean putDate(ObjectNode target, Object value) {
        String date = null;
        if (value instanceof org.joda.time.LocalDate jl) date = jl.toString("yyyy-MM-dd");
        else if (value instanceof LocalDate l) date = l.toString();
        else if (value instanceof org.joda.time.DateTime jdt) date = Instant.ofEpochMilli(jdt.getMillis()).atZone(AVNI_DATE_ZONE).toLocalDate().toString();
        else if (value instanceof Instant i) date = i.atZone(AVNI_DATE_ZONE).toLocalDate().toString();
        else if (value instanceof Date dt) date = dt.toInstant().atZone(AVNI_DATE_ZONE).toLocalDate().toString();
        else if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
                date = trimmed;
            } else {
                // Avni keeps a Date answer as an instant; its calendar day is the day in India (as DateTimeUtil.toDateString).
                Instant instant = AbdmTime.parse(trimmed);
                if (instant != null) date = instant.atZone(AVNI_DATE_ZONE).toLocalDate().toString();
            }
        }
        if (date == null) return putString(target, value);
        target.put("valueDateTime", date);
        return true;
    }

    private static boolean putDateTime(ObjectNode target, Object value) {
        Instant instant = null;
        if (value instanceof Instant i) instant = i;
        else if (value instanceof org.joda.time.DateTime jdt) instant = Instant.ofEpochMilli(jdt.getMillis());
        else if (value instanceof Date dt) instant = dt.toInstant();
        else if (value instanceof String s) instant = AbdmTime.parse(s.trim());
        if (instant == null) return putString(target, value);
        target.put("valueDateTime", AbdmTime.format(instant));
        return true;
    }

    private static boolean putString(ObjectNode target, Object value) {
        if (value instanceof Map || value instanceof Collection || value instanceof Object[]) return false;
        String text = value.toString();
        if (!hasText(text)) return false;
        target.put("valueString", text);
        return true;
    }

    static String fhirGender(String gender) {
        if (gender == null) return "unknown";
        return switch (gender.trim().toLowerCase(Locale.ROOT)) {
            case "m", "male" -> "male";
            case "f", "female" -> "female";
            case "o", "other" -> "other";
            default -> "unknown";
        };
    }

    private static String title(CareContextDocument d) {
        String type = hasText(d.encounterTypeName()) ? d.encounterTypeName() : "Consultation";
        return hasText(d.hipName()) ? type + " — " + d.hipName() : type;
    }

    private static String practitionerName(CareContextDocument d) {
        return hasText(d.practitionerName()) ? d.practitionerName() : "Health worker";
    }

    private static ObjectNode resource(String type, String id, String nrcesProfile) {
        ObjectNode r = AbdmJson.obj();
        r.put("resourceType", type);
        r.put("id", id);
        r.putObject("meta").putArray("profile").add(NRCES + nrcesProfile);
        return r;
    }

    private static ObjectNode entry(ObjectNode resource) {
        ObjectNode e = AbdmJson.obj();
        e.put("fullUrl", "urn:uuid:" + resource.get("id").asText());
        e.set("resource", resource);
        return e;
    }

    private static ObjectNode reference(String id, String display) {
        ObjectNode r = AbdmJson.obj();
        r.put("reference", "urn:uuid:" + id);
        if (hasText(display)) r.put("display", display);
        return r;
    }

    private static ObjectNode codeable(String system, String code, String display) {
        ObjectNode c = AbdmJson.obj();
        ObjectNode coding = c.putArray("coding").addObject();
        coding.put("system", system);
        coding.put("code", code);
        coding.put("display", display);
        c.put("text", display);
        return c;
    }

    // The number carries NRCES's HIN and, as NRCES's own samples do, MR under healthid.ndhm.gov.in.
    private static ObjectNode abhaNumberIdentifier(String value) {
        ObjectNode identifier = AbdmJson.obj();
        ObjectNode type = identifier.putObject("type");
        ArrayNode codings = type.putArray("coding");
        ObjectNode hin = codings.addObject();
        hin.put("system", NDHM_IDENTIFIER_TYPE);
        hin.put("code", "HIN");
        hin.put("display", "Health ID issued by NDHM");
        ObjectNode mr = codings.addObject();
        mr.put("system", V2_0203);
        mr.put("code", "MR");
        mr.put("display", "Medical record number");
        type.put("text", "ABHA Number");
        identifier.put("system", ABHA_SYSTEM);
        identifier.put("value", value);
        return identifier;
    }

    // The address: NRCES's ABHA code, and no system, since NRCES publishes none for it.
    private static ObjectNode abhaAddressIdentifier(String value) {
        ObjectNode identifier = AbdmJson.obj();
        ObjectNode type = identifier.putObject("type");
        ObjectNode coding = type.putArray("coding").addObject();
        coding.put("system", NDHM_IDENTIFIER_TYPE);
        coding.put("code", "ABHA");
        coding.put("display", "Ayushman Bharat Health Account (ABHA) ID");
        type.put("text", "ABHA Address");
        identifier.put("value", value);
        return identifier;
    }

    private static String join(Collection<?> values) {
        return values.stream().filter(Objects::nonNull).map(Object::toString).filter(FhirBundleBuilder::hasText).collect(Collectors.joining(", "));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
