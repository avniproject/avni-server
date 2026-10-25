package org.avni.server.service.abdm.eka;

import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.abha.EkaCredentialCipher;
import org.avni.server.service.abha.EkaNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

// DEMO-ONLY abdm-hackathon: never merged.
// Eka's webhook signature (developer.eka.care connect/webhooks/webhook-signature): header
// "Eka-Webhook-Signature: t=<epoch seconds>,v1=<hex HMAC-SHA256 of t + "." + raw body>", keyed with the signing key given
// at registration as plain UTF-8 text, compared in constant time, within 3 minutes of now.
// The key is the organisation's encrypted webhook signing key in its Eka settings. A webhook has no Avni user, so it is
// read as the configured demo organisation (AbdmOrgContext.runAsOrg). Never logs the key, the header or the body.
@Component
public class EkaWebhookVerifier {
    public static final String HEADER = "Eka-Webhook-Signature";
    public static final long TOLERANCE_SECONDS = 180;

    public record Result(boolean valid, String reason) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result invalid(String reason) {
            return new Result(false, reason);
        }
    }

    private final AbdmOrgContext orgContext;
    private final ExternalSystemConfigRepository configRepository;
    private final EkaCredentialCipher cipher;
    private final Clock clock;

    @Autowired
    public EkaWebhookVerifier(AbdmOrgContext orgContext, ExternalSystemConfigRepository configRepository, EkaCredentialCipher cipher) {
        this(orgContext, configRepository, cipher, Clock.systemUTC());
    }

    public EkaWebhookVerifier(AbdmOrgContext orgContext, ExternalSystemConfigRepository configRepository, EkaCredentialCipher cipher,
                              Clock clock) {
        this.orgContext = orgContext;
        this.configRepository = configRepository;
        this.cipher = cipher;
        this.clock = clock;
    }

    public Result verify(String header, String rawBody) {
        String key;
        try {
            key = signingKey();
        } catch (RuntimeException e) {
            return Result.invalid("the signing key could not be read: " + e.getMessage());
        }
        if (!StringUtils.hasText(key)) return Result.invalid("no webhook signing key in the organisation's Eka settings");
        return check(header, rawBody, key, clock.instant().getEpochSecond());
    }

    private String signingKey() {
        AtomicReference<String> key = new AtomicReference<>();
        orgContext.runAsOrg(() -> {
            EkaSystemConfig config;
            try {
                config = configRepository.getEkaSystemConfig(orgContext.organisationId());
            } catch (EkaNotConfiguredException e) {
                throw new IllegalStateException("Eka is not set up for the organisation");
            }
            String encrypted = config == null ? null : config.getEncryptedWebhookSigningKey();
            key.set(StringUtils.hasText(encrypted) ? cipher.decrypt(encrypted) : null);
        });
        return key.get();
    }

    public static Result check(String header, String rawBody, String key, long nowEpochSeconds) {
        if (header == null || header.isBlank()) return Result.invalid("no " + HEADER + " header");
        String t = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            String name = kv[0].trim();
            if ("t".equals(name)) t = kv[1].trim();
            else if ("v1".equals(name)) signatures.add(kv[1].trim().toLowerCase(Locale.ROOT));
        }
        if (t == null || signatures.isEmpty()) return Result.invalid("the signature header has no t or no v1");
        long timestamp;
        try {
            timestamp = Long.parseLong(t);
        } catch (NumberFormatException e) {
            return Result.invalid("the signature timestamp is not a number");
        }
        byte[] expected = sign(key, t, rawBody).getBytes(StandardCharsets.UTF_8);
        boolean matches = false;
        for (String signature : signatures) {
            matches |= MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        }
        if (!matches) return Result.invalid("the signature does not match the body");
        long skew = nowEpochSeconds - timestamp;
        if (Math.abs(skew) > TOLERANCE_SECONDS) {
            return Result.invalid("the signature is " + skew + " s old, outside " + TOLERANCE_SECONDS + " s");
        }
        return Result.ok();
    }

    // Lowercase hex HMAC-SHA256 of t + "." + rawBody, as Eka computes it.
    public static String sign(String key, String t, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = t + "." + (rawBody == null ? "" : rawBody);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
