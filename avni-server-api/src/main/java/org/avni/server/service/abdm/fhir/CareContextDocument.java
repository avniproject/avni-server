package org.avni.server.service.abdm.fhir;

import org.joda.time.LocalDate;

import java.time.Instant;
import java.util.List;

// DEMO-ONLY abdm-hackathon: never merged.
// One care context (one Avni encounter) ready to become a FHIR document: the patient, the visit and its answers.
public record CareContextDocument(String encounterUuid, String encounterTypeName, Instant encounterDateTime,
                                  String patientUuid, String patientName, String gender, LocalDate dateOfBirth,
                                  String abhaNumber, String abhaAddress, String practitionerName,
                                  String hipId, String hipName, List<Item> items) {
    // One answer. dataType is the Avni concept data type (Numeric, Coded, Text, Date, DateTime, ...).
    // One answer. codes: the question's standard codes (SNOMED CT, LOINC) from its concept, empty when it has none.
    public record Item(String name, String dataType, Object value, String unit, List<Code> codes) {
        public Item(String name, String dataType, Object value, String unit) {
            this(name, dataType, value, unit, List.of());
        }
    }

    public record Code(String system, String code) {
    }
}
