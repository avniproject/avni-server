package org.avni.server.service.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// DEMO-ONLY abdm-hackathon: what the traffic log keeps of a message (AVNI-13: 32 of 64 stored messages carried a patient
// identifier in plain text on 26 Oct).
public class AbdmMessageLogTest {
    private static final String MASK = AbdmMessageLog.MASK;

    @Test
    public void aWebhookKeepsItsIdsAndStatusesAndLosesThePatient() {
        JsonNode kept = AbdmMessageLog.redact(AbdmJson.read("{\"service\":\"abdm\",\"event\":\"abha.link_care_context\"," +
                "\"transaction_id\":\"wh-link-1\",\"data\":{\"abha_address\":\"demo.patient@sbx\",\"hip_id\":\"TANUH\"," +
                "\"care_context_id\":\"enc-1\",\"status\":\"LINKED\",\"error\":null,\"oid\":\"eka-oid-0001\",\"partner_patient_id\":\"subject-1\"}}"));

        assertEquals("abha.link_care_context", kept.get("event").asText());
        assertEquals("wh-link-1", kept.get("transaction_id").asText());
        assertEquals("TANUH", kept.at("/data/hip_id").asText());
        assertEquals("enc-1", kept.at("/data/care_context_id").asText());
        assertEquals("LINKED", kept.at("/data/status").asText());
        assertTrue(kept.at("/data/error").isNull());
        assertEquals(MASK, kept.at("/data/abha_address").asText());
        assertEquals(MASK, kept.at("/data/oid").asText());
        assertEquals(MASK, kept.at("/data/partner_patient_id").asText());
    }

    @Test
    public void namesNumbersAndAnyUnlistedFieldAreMaskedWhereverTheySit() {
        JsonNode kept = AbdmMessageLog.redact(AbdmJson.read("{\"request_id\":\"r-1\",\"patient\":{\"id\":\"shyam@sbx\",\"name\":\"Shyam\"," +
                "\"gender\":\"M\",\"yearOfBirth\":1990,\"identifiers\":[{\"type\":\"MOBILE\",\"value\":\"9876543210\"}]}," +
                "\"abhaNumber\":\"91-1234-5678-9012\",\"data\":\"eyJyZXNvdXJjZVR5cGUiOiJCdW5kbGUifQ==\"}"));

        assertEquals("r-1", kept.get("request_id").asText());
        assertEquals(MASK, kept.at("/patient/id").asText());
        assertEquals(MASK, kept.at("/patient/name").asText());
        assertEquals(MASK, kept.at("/patient/gender").asText());
        assertEquals(MASK, kept.at("/patient/yearOfBirth").asText());
        assertEquals(MASK, kept.at("/patient/identifiers/0/value").asText());
        assertEquals(MASK, kept.get("abhaNumber").asText());
        assertEquals(MASK, kept.get("data").asText());
        assertFalse(AbdmJson.write(kept).contains("9876543210"));
    }

    @Test
    public void freeTextKeptForSupportLosesAnyIdentifierInIt() {
        JsonNode kept = AbdmMessageLog.redact(AbdmJson.read("{\"request_id\":\"r-1\",\"error\":{\"code\":\"ABDM-1035\"," +
                "\"message\":\"No ABHA asha.rao@sbx for 9876543210\"}}"));

        assertEquals("r-1", kept.get("request_id").asText());
        assertEquals("ABDM-1035", kept.at("/error/code").asText());
        String message = kept.at("/error/message").asText();
        assertFalse(message, message.contains("asha.rao@sbx"));
        assertFalse(message, message.contains("9876543210"));
        assertTrue(message, message.startsWith("No ABHA "));
    }

    @Test
    public void aConsentUpdateKeepsItsArtefactIds() {
        JsonNode kept = AbdmMessageLog.redact(AbdmJson.read("{\"data\":{\"notification\":{\"consentRequestId\":\"cr-1\",\"status\":\"GRANTED\"," +
                "\"consentArtefacts\":[{\"id\":\"artefact-1\"}]}}}"));

        assertEquals("cr-1", kept.at("/data/notification/consentRequestId").asText());
        assertEquals("GRANTED", kept.at("/data/notification/status").asText());
        assertEquals("artefact-1", kept.at("/data/notification/consentArtefacts/0/id").asText());
    }

    @Test
    public void aRecordPushKeepsNeitherTheRecordNorTheKeys() {
        JsonNode kept = AbdmMessageLog.redact(AbdmJson.read("{\"data\":{\"consent_id\":\"cr-1\",\"entries\":[{\"care_context_id\":\"cc-1\"," +
                "\"content\":\"ENCRYPTED\",\"checksum\":\"abc\"}],\"key_information\":{\"dh_public_key\":{\"key_value\":\"KEY\"},\"nonce\":\"NONCE\"}}}"));

        assertEquals("cr-1", kept.at("/data/consent_id").asText());
        assertEquals("cc-1", kept.at("/data/entries/0/care_context_id").asText());
        assertEquals(MASK, kept.at("/data/entries/0/content").asText());
        assertEquals(MASK, kept.at("/data/key_information/dh_public_key/key_value").asText());
        assertEquals(MASK, kept.at("/data/key_information/nonce").asText());
    }

    @Test
    public void nothingInNothingOut() {
        assertTrue(AbdmMessageLog.redact(null) == null);
    }
}
