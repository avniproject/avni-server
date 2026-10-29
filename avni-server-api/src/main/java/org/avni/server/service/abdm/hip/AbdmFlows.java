package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmTime;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// DEMO-ONLY abdm-hackathon: never merged.
// Small pieces both the HIP and the HIU flows use.
public final class AbdmFlows {
    public static final String CRYPTO_ALG = "ECDH";
    public static final String CURVE = "Curve25519";
    public static final String KEY_PARAMETERS = "Curve25519/32byte random key";

    private AbdmFlows() {
    }

    public static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    // 14 digits as ABDM writes them: xx-xxxx-xxxx-xxxx. Anything else comes back as given.
    public static String hyphenatedAbhaNumber(String number) {
        String d = digitsOnly(number);
        if (d.length() != 14) return number;
        return d.substring(0, 2) + "-" + d.substring(2, 6) + "-" + d.substring(6, 10) + "-" + d.substring(10);
    }

    // For screens: only the last four digits.
    public static String maskedAbhaNumber(String number) {
        String d = digitsOnly(number);
        if (d.length() < 4) return null;
        return "xx-xxxx-xxxx-" + d.substring(d.length() - 4);
    }

    // Eka's free-text errors can quote the patient (AVNI-10). Masks an ABHA number (14 digits, 2-4-4-4), an Aadhaar
    // (12 digits, 4-4-4), an Indian mobile (10 digits from 6-9, or 5-5, with or without +91) and an ABHA address, and
    // keeps everything else. A digit run is matched only whole, so UUID segments, ABDM codes and timestamps survive;
    // an all-digit Eka id of 10, 12 or 14 digits would be masked too (Eka's id formats are undocumented).
    public static String maskIdentifiers(String text) {
        if (text == null) return null;
        String masked = text;
        for (Pattern identifier : IDENTIFIERS) {
            masked = identifier.matcher(masked).replaceAll("***");
        }
        return masked;
    }

    private static final String WHOLE_START = "(?<![\\w-])";
    private static final String WHOLE_END = "(?![\\w-])";
    private static final List<Pattern> IDENTIFIERS = List.of(
            Pattern.compile(WHOLE_START + "\\d{2}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}" + WHOLE_END),
            Pattern.compile(WHOLE_START + "\\d{4}[- ]?\\d{4}[- ]?\\d{4}" + WHOLE_END),
            Pattern.compile(WHOLE_START + "(?:\\+?91[- ]?)?(?:[6-9]\\d{9}|[6-9]\\d{4} \\d{5})" + WHOLE_END),
            Pattern.compile("[\\w.]+@(?:sbx|abdm)\\b", Pattern.CASE_INSENSITIVE));

    public static String withSuffix(String abhaAddress, String suffix) {
        if (abhaAddress == null || abhaAddress.isBlank()) return null;
        String a = abhaAddress.trim();
        return a.contains("@") || suffix == null ? a : a + suffix;
    }

    // DEMO-ONLY: ABDM's checksum algorithm is unverified; md5 hex of the plaintext bundle, logged, never enforced.
    public static String md5Hex(String plaintext) {
        return DigestUtils.md5DigestAsHex(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        if (array == null || !array.isArray()) return out;
        for (JsonNode n : array) {
            String v = field == null ? (n.isValueNode() ? n.asText() : null) : AbdmJson.text(n, field);
            if (v != null) out.add(v);
        }
        return out;
    }

    public static String iso(Instant instant) {
        return instant == null ? null : AbdmTime.format(instant);
    }

    public static String firstNonNull(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
