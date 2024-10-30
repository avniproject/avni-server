package org.avni.server.framework.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.avni.server.web.api.ApiRequestContext;
import org.avni.server.web.api.ApiRequestContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiResourceInterceptor implements HandlerInterceptor {
    public static final String ONE = new Integer(1).toString();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object object) {
        ApiRequestContextHolder.create(new ApiRequestContext(getVersion(request)));
        return true;
    }

    private String getVersion(HttpServletRequest request) {
        String version = request.getParameter("version");
        version = version == null ? ONE : version;
        return version;
    }
}
