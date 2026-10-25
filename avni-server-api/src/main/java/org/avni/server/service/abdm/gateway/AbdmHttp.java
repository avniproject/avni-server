package org.avni.server.service.abdm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.avni.server.service.abdm.AbdmJson;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// DEMO-ONLY abdm-hackathon: never merged.
// The JDK HTTP client as EkaRestClient uses it: HTTP/1.1, a connect timeout, a deadline over the whole exchange, and a
// Content-Length body. Writes nothing to Logback. Failure messages name the call and the HTTP status or failure kind,
// never a header value or a body we sent.
final class AbdmHttp {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ABDM_MESSAGE = 300;

    private AbdmHttp() {
    }

    static HttpClient newClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
    }

    static HttpRequest.BodyPublisher json(JsonNode body) {
        return HttpRequest.BodyPublishers.ofString(body == null ? "" : AbdmJson.write(body));
    }

    static HttpResponse<String> send(HttpClient httpClient, HttpRequest request, Duration answerTimeout, String call) {
        CompletableFuture<HttpResponse<String>> answer = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            return answer.get(answerTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            answer.cancel(true);
            throw new AbdmGatewayException(0, null, call + " did not answer within " + answerTimeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new AbdmGatewayException(0, null, call + " did not answer: " + cause.getClass().getSimpleName());
        } catch (InterruptedException e) {
            answer.cancel(true);
            Thread.currentThread().interrupt();
            throw new AbdmGatewayException(0, null, call + " did not answer: interrupted");
        }
    }

    static boolean ok(int status) {
        return status >= 200 && status <= 299;
    }

    // JSON when the body is JSON, the text when it is not, null when there is none.
    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return AbdmJson.M.readTree(body);
        } catch (Exception e) {
            return TextNode.valueOf(body);
        }
    }

    // A refusal, with ABDM's own code and message when its body names them: {"error":{"code","message"}} or a top-level
    // {"code","message"}. ABDM's message is kept because it is the only explanation the demo operator gets.
    static AbdmGatewayException refusal(String call, int status, JsonNode body) {
        String code = null;
        String message = null;
        if (body != null && body.isObject()) {
            JsonNode error = body.get("error");
            if (error != null && error.isObject()) {
                code = AbdmJson.text(error, "code");
                message = AbdmJson.text(error, "message");
            }
            if (code == null) code = AbdmJson.text(body, "code");
            if (message == null) message = AbdmJson.text(body, "message");
        }
        StringBuilder text = new StringBuilder(call).append(" refused with HTTP ").append(status);
        if (code != null) text.append(" ").append(code);
        if (message != null) {
            text.append(": ").append(message.length() > MAX_ABDM_MESSAGE ? message.substring(0, MAX_ABDM_MESSAGE) : message);
        }
        return new AbdmGatewayException(status, code, text.toString());
    }
}
