package org.avni.server.framework.sync;

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@Order()
public class MutableRequestFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(MutableRequestFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        request.setCharacterEncoding("UTF8");
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        chain.doFilter(new MutableRequestWrapper(new XSSSafeRequest(httpServletRequest)), response);
    }

    @Override
    public void destroy() {
    }

    public static class XSSSafeRequest extends HttpServletRequestWrapper {
        public XSSSafeRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            HttpServletRequest request = (HttpServletRequest) this.getRequest();
            return Encode.forHtml(request.getParameter(name));
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> existingParameterMap = super.getParameterMap();
            if (existingParameterMap == null) return null;

            Map<String, String[]> newParameterMap = new HashMap<>();
            for (String key : existingParameterMap.keySet()) {
                newParameterMap.put(key, getParameterValues(key));
            }
            return newParameterMap;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] existingValues = super.getParameterValues(name);
            if (existingValues == null) return null;

            String[] newValues = new String[existingValues.length];
            for (int i = 0; i < existingValues.length; i++) {
                newValues[i] = Encode.forHtml(existingValues[i]);
            }
            return newValues;
        }
    }
}
