package org.avni.server.framework.sync;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.rest.webmvc.support.DefaultedPageable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring's pageable resolver quietly serves the maximum page size when asked for more, and an empty page
 * when the size is not a number, both with a 200. A client paging until it gets a short page would then
 * stop early and believe it has everything (#1058). Refuse such requests instead.
 */
@Component
public class PageSizeGuardInterceptor implements HandlerInterceptor {
    static final String SIZE_PARAM = "size";
    private int maxPageSize;

    public PageSizeGuardInterceptor(@Value("${avni.sync.max.page.size:1000}") int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // getParameterValues, not getParameter: MutableRequestFilter's XSS wrapper turns an absent parameter
        // into the string "null" through getParameter.
        String[] values = request.getParameterValues(SIZE_PARAM);
        if (values == null || values.length == 0 || !takesPageable(handler)) return true;
        String size = values[0];
        if (size == null || size.isBlank()) return true;

        int asked;
        try {
            asked = Integer.parseInt(size.trim());
        } catch (NumberFormatException e) {
            return refuse(response, String.format("Page size '%s' is not a whole number. The maximum page size allowed is %d.", size, maxPageSize));
        }
        if (asked > maxPageSize) {
            return refuse(response, String.format("Page size %d is more than the maximum page size allowed, %d. Ask for %d or fewer.", asked, maxPageSize, maxPageSize));
        }
        return true;
    }

    // Written directly: sendError's message is dropped from the error body by Spring Boot's default
    // server.error.include-message=never, and the caller needs it to act.
    private static boolean refuse(HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
        return false;
    }

    private static boolean takesPageable(Object handler) {
        if (!(handler instanceof HandlerMethod)) return false;
        for (MethodParameter parameter : ((HandlerMethod) handler).getMethodParameters()) {
            Class<?> type = parameter.getParameterType();
            if (Pageable.class.isAssignableFrom(type) || DefaultedPageable.class.isAssignableFrom(type)) return true;
        }
        return false;
    }
}
