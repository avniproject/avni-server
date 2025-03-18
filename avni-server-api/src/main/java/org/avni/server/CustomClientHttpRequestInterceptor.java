package org.avni.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * interceptor to log incoming requests
 */
public class CustomClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(CustomClientHttpRequestInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        logRequestDetails(request);

        return execution.execute(request, body);
    }

    private void logRequestDetails(HttpRequest request) {
        logger.debug("Request details {}, {}, {}", request.getURI(), request.getMethod(), request.getHeaders());
    }
}
