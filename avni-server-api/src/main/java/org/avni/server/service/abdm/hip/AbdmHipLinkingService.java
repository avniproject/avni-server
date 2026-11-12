package org.avni.server.service.abdm.hip;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaLink;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSettings;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abdm.AbdmSettings;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// The HIP's view of a subject: their completed visits as care contexts, each with its link state. EkaHipService links
// them through Eka. Statuses: PENDING -> LINK_REQUESTED -> LINKED | FAILED | FAILED_FINAL. A visit queued on save
// (AbdmVisitQueue) starts QUEUED instead, and only those are linked by AbdmHousekeepingJob. FAILED is tried again after
// a growing wait; FAILED_FINAL never is; AWAITING_VERIFIED_ABHA waits for the patient's ABHA to be verified with Eka
// (AbhaRecordSaved) and is then queued again (AbdmHipStore's link queue).
// A visit is shared under one of the organisation's HIP IDs: the one its care context was made under, else the HIP ID of
// the user who recorded it when that is one of the organisation's, else the organisation's first (visitsByHip).
@Service
public class AbdmHipLinkingService {
    public static final String HI_TYPE = "OPConsultation";
    public static final String PENDING = "PENDING";
    public static final String QUEUED = "QUEUED";
    public static final String LINK_REQUESTED = "LINK_REQUESTED";
    public static final String LINKED = "LINKED";
    public static final String FAILED = "FAILED";
    public static final String FAILED_FINAL = "FAILED_FINAL";
    // The patient's ABHA is not verified with Eka yet: the visit waits, untried, until a record of it with an Eka ID is saved.
    public static final String AWAITING_VERIFIED_ABHA = "AWAITING_VERIFIED_ABHA";
    public static final String NO_EKA_ID = "Patient has no Eka ID: register or verify their ABHA through the app first";

    // dates shown to the patient in India's time.
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormat.forPattern("d MMM yyyy").withLocale(Locale.ENGLISH)
            .withZone(DateTimeZone.forID("Asia/Kolkata"));

    private final AbdmSettings settings;
    private final AbdmHipStore store;
    private final AbdmOrgContext orgContext;
    private final IndividualRepository individualRepository;
    private final AbhaLinkRepository abhaLinkRepository;

    public AbdmHipLinkingService(AbdmSettings settings, AbdmHipStore store, AbdmOrgContext orgContext,
                                 IndividualRepository individualRepository, AbhaLinkRepository abhaLinkRepository) {
        this.settings = settings;
        this.store = store;
        this.orgContext = orgContext;
        this.individualRepository = individualRepository;
        this.abhaLinkRepository = abhaLinkRepository;
    }

    // One visit that can be shared: an encounter's uuid, its display, when it happened, and the HIP ID in the settings of
    // the user who recorded it (any, or none).
    public record Visit(String reference, String display, Instant at, String recorderHipId) {
        public Visit(String reference, String display, Instant at) {
            this(reference, display, at, null);
        }
    }

    // A subject as linking needs it, read in one read-only transaction.
    public record Patient(long individualId, String uuid, String name, String gender, Integer yearOfBirth, String abhaNumber,
                          String abhaAddress, List<Visit> visits) {
    }

    // ---- web

    // Every patient with an ABHA, with their visits linked under any of the organisation's HIP IDs.
    public List<Map<String, Object>> patientsWithAbha() {
        EkaSystemConfig orgSettings = settings.current();
        Map<String, Map<String, Object>> bySubject = new LinkedHashMap<>();
        for (String hipId : orgSettings.getHipIds()) {
            for (AbdmHipStore.PatientRow r : store.patientsWithAbha(hipId)) {
                Map<String, Object> m = bySubject.get(r.subjectUuid());
                if (m != null) {
                    m.put("linked", (Long) m.get("linked") + r.linked());
                    continue;
                }
                m = new LinkedHashMap<>();
                m.put("subjectUUID", r.subjectUuid());
                m.put("name", r.name());
                m.put("abhaNumberMasked", AbdmFlows.maskedAbhaNumber(r.abhaNumber()));
                m.put("abhaAddress", AbdmFlows.withSuffix(r.abhaAddress(), orgSettings.getAbhaAddressSuffix()));
                m.put("encounters", r.encounters());
                m.put("linked", r.linked());
                bySubject.put(r.subjectUuid(), m);
            }
        }
        return new ArrayList<>(bySubject.values());
    }

    public List<Map<String, Object>> careContexts(String subjectUuid) {
        return view(loadPatient(subjectUuid));
    }

    // ---- reading

    public Patient loadPatient(String subjectUuid) {
        Patient patient = orgContext.inReadOnlyTransaction(() -> {
            Individual individual = individualRepository.findByUuid(subjectUuid);
            if (individual == null || individual.isVoided()) return null;
            return toPatient(individual, abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(individual.getId()).orElse(null));
        });
        if (patient == null) throw AbdmRequestException.notFound("Subject not found");
        return patient;
    }

    // Call inside a transaction: encounters are lazy.
    public static Patient toPatient(Individual individual, AbhaLink abhaLink) {
        Integer yearOfBirth = individual.getDateOfBirth() == null ? null : individual.getDateOfBirth().getYear();
        return new Patient(individual.getId(), individual.getUuid(), individual.getFullName(), genderLetter(individual.getGenderName()),
                yearOfBirth, abhaLink == null ? null : abhaLink.getAbhaNumber(), abhaLink == null ? null : abhaLink.getAbhaAddress(),
                visits(individual));
    }

    // Completed, not cancelled, not voided, general and programme visits (of enrolments not voided); newest first.
    public static List<Visit> visits(Individual individual) {
        java.util.stream.Stream<org.avni.server.domain.AbstractEncounter> general = individual.nonVoidedEncounters().map(e -> e);
        java.util.stream.Stream<org.avni.server.domain.AbstractEncounter> programme = individual.getProgramEnrolments() == null
                ? java.util.stream.Stream.empty()
                : individual.getProgramEnrolments().stream().filter(p -> !p.isVoided()).flatMap(p -> p.nonVoidedEncounters().map(e -> e));
        return java.util.stream.Stream.concat(general, programme)
                .filter(e -> e.getEncounterDateTime() != null && e.getCancelDateTime() == null)
                .sorted(Comparator.comparing((org.avni.server.domain.AbstractEncounter e) -> e.getEncounterDateTime()).reversed())
                .map(e -> new Visit(e.getUuid(), display(e), e.getEncounterDateTime().toDate().toInstant(), recorderHipId(e)))
                .collect(Collectors.toList());
    }

    // The HIP ID in the settings of the user who recorded the visit; null when there is none.
    public static String recorderHipId(org.avni.server.domain.AbstractEncounter encounter) {
        User recorder = encounter.getCreatedBy();
        if (recorder == null || recorder.getSettings() == null) return null;
        Object hipId = recorder.getSettings().get(UserSettings.ABHA_HIP_ID);
        return hipId instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    // The patient's visits by the HIP ID each is shared under, in their order (see the class comment). A visit keeps the
    // HIP ID its care context was made under, so it is never linked twice.
    public static Map<String, List<Visit>> visitsByHip(Patient patient, EkaSystemConfig orgSettings, AbdmHipStore store) {
        if (orgSettings.getHipIds().isEmpty()) throw AbdmRequestException.conflict("The organisation has no HIP ID");
        Map<String, String> madeUnder = new HashMap<>();
        for (String hipId : orgSettings.getHipIds()) {
            for (AbdmHipStore.CareContext c : store.findCareContexts(patient.individualId(), hipId)) madeUnder.putIfAbsent(c.reference(), hipId);
        }
        Map<String, List<Visit>> byHip = new LinkedHashMap<>();
        for (Visit v : patient.visits()) {
            String hipId = madeUnder.containsKey(v.reference()) ? madeUnder.get(v.reference()) : orgSettings.hipIdOr(v.recorderHipId());
            byHip.computeIfAbsent(hipId, k -> new ArrayList<>()).add(v);
        }
        return byHip;
    }

    public static String display(org.avni.server.domain.AbstractEncounter encounter) {
        String type = encounter.getEncounterType() == null ? "Visit" : encounter.getEncounterType().getName();
        return type + ", " + DISPLAY_DATE.print(encounter.getEncounterDateTime());
    }

    static String genderLetter(String genderName) {
        if (genderName == null) return "O";
        String g = genderName.trim().toLowerCase(Locale.ENGLISH);
        if (g.startsWith("m")) return "M";
        if (g.startsWith("f")) return "F";
        return "O";
    }

    // Every visit with its link state, plus rows whose visit has since gone, under any of the organisation's HIP IDs.
    private List<Map<String, Object>> view(Patient patient) {
        Map<String, AbdmHipStore.CareContext> rows = new LinkedHashMap<>();
        for (String hipId : settings.current().getHipIds()) {
            for (AbdmHipStore.CareContext c : store.findCareContexts(patient.individualId(), hipId)) rows.putIfAbsent(c.reference(), c);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Visit v : patient.visits()) {
            AbdmHipStore.CareContext row = rows.remove(v.reference());
            out.add(row != null ? view(row) : view(v.reference(), v.display(), HI_TYPE, PENDING, null, null));
        }
        for (AbdmHipStore.CareContext row : rows.values()) out.add(view(row));
        return out;
    }

    private static Map<String, Object> view(AbdmHipStore.CareContext c) {
        return view(c.reference(), c.display(), c.hiType(), c.status(), c.error(), c.updatedAt());
    }

    private static Map<String, Object> view(String reference, String display, String hiType, String status, String error, Instant updatedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reference", reference);
        m.put("display", display);
        m.put("hiType", hiType);
        m.put("status", status);
        m.put("error", error);
        m.put("updatedAt", AbdmFlows.iso(updatedAt));
        return m;
    }
}
