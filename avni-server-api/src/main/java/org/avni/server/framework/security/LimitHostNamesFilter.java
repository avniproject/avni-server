package org.avni.server.framework.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;

import static org.springframework.util.StringUtils.isEmpty;

@Component
@Order()
public class LimitHostNamesFilter extends OncePerRequestFilter {
    private final String[] validHosts;
    private final Logger logger = LoggerFactory.getLogger(LimitHostNamesFilter.class);

    public LimitHostNamesFilter(@Value("${avni.web.validHosts}") String[] validHosts) {
        this.validHosts = validHosts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String errorMessage = validate(request.getServerName());

        if (isEmpty(errorMessage)) {
            filterChain.doFilter(request, response);
        } else {
            String msg = "Request rejected due to invalid host";
            logger.warn(String.format("%s: %s", msg, request.getServerName()));
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        }
    }

    private String validate(String hostHeader) {
        if (validHosts == null || validHosts.length == 0) {
            return "";
        }

        if (isEmpty(hostHeader)) {
            return "Host header is empty";
        }

        if (Arrays.stream(validHosts).noneMatch(hostHeader::equalsIgnoreCase)) {
            return "Incorrect host header";
        }

        return "";
    }
}
