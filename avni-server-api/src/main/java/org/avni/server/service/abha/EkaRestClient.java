package org.avni.server.service.abha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

// Logs in to Eka Care and fetches ABHA cards, on the JDK HTTP client directly:
// - its deadline covers the whole exchange, so an answer whose body stalls fails at 10 s too;
// - it writes nothing to Logback at any log level, where RestTemplate and Apache HttpClient print request bodies at DEBUG;
// - it sends a Content-Length, never a chunked body.
// Renewal is a fresh login. Eka's sandbox answers refresh-token without expires_in (checked 20 Oct 2026), so a
// refreshed token's lifetime is unknown. Never log a request or response body here; both carry secrets. A card request
// carries the organisation's token and the patient's Eka ID, and its answer is a picture of the patient's identity.
@Service
public class EkaRestClient {
    private static final String LOGIN_PATH = "/connect-auth/v1/account/login";
    private static final String CARD_PATH = "/abdm/v1/profile/asset/card";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);
    // An ABHA card is one picture; anything larger is not one, and is not held in memory to find out.
    static final int MAX_CARD_BYTES = 5 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public EkaToken login(String baseUrl, String clientId, String clientSecret) {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + LOGIN_PATH))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(toJson(body)))
                .build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray(), "Eka login", EkaAuthException::new);
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new EkaAuthException("Eka login failed with HTTP " + response.statusCode());
        }
        return toToken(parse(response.body()));
    }

    // The patient's ABHA card as Eka draws it, a PNG, for the patient's Eka ID (developer.eka.care, profile/cards/abha-card).
    // Failures name the HTTP status or the kind of failure only. The cause is dropped, so no exception text can carry
    // the request's address, which holds the Eka ID.
    public byte[] getAbhaCard(String baseUrl, String accessToken, String ekaOid, String hipId) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + CARD_PATH + "?oid=" + URLEncoder.encode(ekaOid, StandardCharsets.UTF_8).replace("+", "%20")))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Pt-Id", ekaOid)
                    .header("X-Hip-Id", hipId)
                    .header("Accept", "image/png")
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new EkaCardException("Eka card request not sent: the Eka ID or health centre cannot be sent in a header");
        }
        HttpResponse<byte[]> response = send(request, EkaRestClient::cardBody, "Eka card request", (message, cause) -> new EkaCardException(message));
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            throw new EkaCardException("Eka card request failed with HTTP " + response.statusCode());
        }
        if (!isPng(response.body())) {
            throw new EkaCardException("Eka card request answered HTTP " + response.statusCode() + " with something other than a PNG image");
        }
        return response.body();
    }

    private <E extends RuntimeException> HttpResponse<byte[]> send(HttpRequest request, HttpResponse.BodyHandler<byte[]> bodyHandler,
                                                                   String call, BiFunction<String, Throwable, E> failure) {
        CompletableFuture<HttpResponse<byte[]>> answer = httpClient.sendAsync(request, bodyHandler);
        try {
            return answer.get(ANSWER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            answer.cancel(true);
            throw failure.apply(call + " did not answer within " + ANSWER_TIMEOUT.getSeconds() + " s", null);
        } catch (ExecutionException e) {
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof AnswerTooLargeException) {
                    throw failure.apply(call + " " + cause.getMessage(), null);
                }
            }
            throw failure.apply(call + " failed: " + e.getCause().getClass().getSimpleName(), e.getCause());
        } catch (InterruptedException e) {
            answer.cancel(true);
            Thread.currentThread().interrupt();
            throw failure.apply(call + " was interrupted", null);
        }
    }

    // An error's body is discarded unread. A card's is read up to the cap: one declared larger fails before any of it is
    // read, and one sent without a length fails as soon as it passes the cap, ending the transfer.
    private static HttpResponse.BodySubscriber<byte[]> cardBody(HttpResponse.ResponseInfo info) {
        if (info.statusCode() < 200 || info.statusCode() > 299) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        long declared = info.headers().firstValueAsLong("Content-Length").orElse(-1);
        return new CappedBody(MAX_CARD_BYTES, declared > MAX_CARD_BYTES);
    }

    private static boolean isPng(byte[] body) {
        return body != null && body.length >= PNG_SIGNATURE.length
                && Arrays.equals(Arrays.copyOf(body, PNG_SIGNATURE.length), PNG_SIGNATURE);
    }

    private static final class AnswerTooLargeException extends IOException {
        private AnswerTooLargeException(int limit) {
            super("answered with more than " + limit + " bytes");
        }
    }

    private static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private CappedBody(int limit, boolean declaredTooLarge) {
            this.limit = limit;
            if (declaredTooLarge) {
                body.completeExceptionally(new AnswerTooLargeException(limit));
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (body.isDone()) {
                subscription.cancel();
            } else {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                if ((long) received.size() + buffer.remaining() > limit) {
                    subscription.cancel();
                    body.completeExceptionally(new AnswerTooLargeException(limit));
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                received.write(bytes, 0, bytes.length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(received.toByteArray());
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }
    }

    private static Map<String, Object> parse(byte[] body) {
        try {
            return body == null || body.length == 0 ? null : objectMapper.readValue(body, RESPONSE_TYPE);
        } catch (IOException e) {
            throw new EkaAuthException("Eka login returned a body that is not JSON");
        }
    }

    private static EkaToken toToken(Map<String, Object> response) {
        if (response == null) {
            throw new EkaAuthException("Eka login returned no body");
        }
        Object accessToken = response.get("access_token");
        Object expiresIn = response.get("expires_in");
        if (!(accessToken instanceof String) || ((String) accessToken).isEmpty() || !(expiresIn instanceof Number)) {
            throw new EkaAuthException("Eka login returned no access_token or expires_in");
        }
        return new EkaToken((String) accessToken, Instant.now().plusSeconds(((Number) expiresIn).longValue()));
    }

    private static byte[] toJson(Map<String, String> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new EkaAuthException("Could not write the Eka request body", e);
        }
    }
}
