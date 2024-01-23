package org.avni.server.framework.sync;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.framework.json.JsonEncoder;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component
@Order()
public class MutableRequestFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(MutableRequestFilter.class);
    
    @Value("${avni.web.payload.encoding}")
    private boolean payloadEncoding;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        request.setCharacterEncoding("UTF8");
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        chain.doFilter(new MutableRequestWrapper(new XSSSafeRequest(httpServletRequest, payloadEncoding)), response);
    }

    @Override
    public void destroy() {
    }

    public static class XSSSafeRequest extends HttpServletRequestWrapper {
        private String body;
        private final boolean doPayloadEncoding;

        public XSSSafeRequest(HttpServletRequest request, boolean doPayloadEncoding) throws IOException {
            super(request);
            this.doPayloadEncoding = doPayloadEncoding;
            if (!doPayloadEncoding) return;

            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = null;
            try {
                InputStream inputStream = request.getInputStream();
                if (inputStream != null) {
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    char[] charBuffer = new char[128];
                    int bytesRead = -1;
                    while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                        stringBuilder.append(charBuffer, 0, bytesRead);
                    }
                } else {
                    stringBuilder.append("");
                }
            } finally {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
            }
            JsonNode jsonNode = JsonEncoder.encode(stringBuilder.toString());
            // Finally store updated request body content in 'body' variable
            this.body = jsonNode.toString();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (!doPayloadEncoding) return super.getInputStream();
            
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body.getBytes());
            ServletInputStream servletInputStream = new ServletInputStream() {
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return false;
                }

                @Override
                public boolean isReady() {
                    return false;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                }
            };
            return servletInputStream;
        }

        private boolean isNotProtected() {
            HttpServletRequest request = (HttpServletRequest) this.getRequest();
            return request.getRequestURI().startsWith("/api");
        }

        @Override
        public String getParameter(String name) {
            HttpServletRequest request = (HttpServletRequest) this.getRequest();
            if (this.isNotProtected())
                return super.getParameter(name);

            return Encode.forHtml(request.getParameter(name));
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> existingParameterMap = super.getParameterMap();
            if (this.isNotProtected())
                return existingParameterMap;

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
            if (this.isNotProtected())
                return existingValues;

            if (existingValues == null) return null;

            String[] newValues = new String[existingValues.length];
            for (int i = 0; i < existingValues.length; i++) {
                newValues[i] = Encode.forHtml(existingValues[i]);
            }
            return newValues;
        }
    }
}
