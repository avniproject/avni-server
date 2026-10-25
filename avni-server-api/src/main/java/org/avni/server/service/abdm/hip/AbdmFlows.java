package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.AbdmTime;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public static String withSuffix(String abhaAddress, String suffix) {
        if (abhaAddress == null || abhaAddress.isBlank()) return null;
        String a = abhaAddress.trim();
        return a.contains("@") || suffix == null ? a : a + suffix;
    }

    // "CODE: message" from an ABDM error object, or null when the body carries none.
    public static String errorOf(JsonNode body) {
        JsonNode error = AbdmJson.at(body, "error");
        if (error == null || error.isNull() || error.isMissingNode()) return null;
        if (!error.isObject()) return error.asText();
        String code = AbdmJson.text(error, "code");
        String message = AbdmJson.text(error, "message");
        if (code == null && message == null) return null;
        if (code == null) return message;
        return message == null ? code : code + ": " + message;
    }

    public static ObjectNode error(String code, String message) {
        ObjectNode e = AbdmJson.obj();
        e.put("code", code);
        e.put("message", message);
        return e;
    }

    public static ObjectNode responseTo(String callbackRequestId) {
        ObjectNode r = AbdmJson.obj();
        r.put("requestId", callbackRequestId);
        return r;
    }

    // DEMO-ONLY: ABDM's checksum algorithm is unverified; md5 hex of the plaintext bundle, logged, never enforced.
    public static String md5Hex(String plaintext) {
        return DigestUtils.md5DigestAsHex(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public static ObjectNode keyMaterial(String publicKey, String nonce, Instant expiry) {
        ObjectNode km = AbdmJson.obj();
        km.put("cryptoAlg", CRYPTO_ALG);
        km.put("curve", CURVE);
        ObjectNode dh = km.putObject("dhPublicKey");
        dh.put("expiry", AbdmTime.format(expiry));
        dh.put("parameters", KEY_PARAMETERS);
        dh.put("keyValue", publicKey);
        km.put("nonce", nonce);
        return km;
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
