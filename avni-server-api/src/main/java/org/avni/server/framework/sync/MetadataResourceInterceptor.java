package org.avni.server.framework.sync;

import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.avni.server.util.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;

@Component
public class MetadataResourceInterceptor extends HandlerInterceptorAdapter {
    private final UserUtil userUtil;

    private static final HashSet<String> endOfLifeEndpoints1 = new HashSet<String>() {{
        add("/dashboardFilter/search/lastModified");
        add("/card/search/lastModified");
        add("/dashboardSection/search/lastModified");
        add("/dashboardSectionCardMapping/search/lastModified");
        add("/dashboard/search/lastModified");
        add("/groupDashboard/search/lastModified");
    }};

    @Value("${avni.endpoints.endOfLife.1}")
    private String endpointEndOfLife1;

    @Autowired
    public MetadataResourceInterceptor(UserUtil userUtil) {
        this.userUtil = userUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object object) throws Exception {
        if (request.getMethod().equals(RequestMethod.GET.name())) {
            DateTime now = new DateTime();
            if (endOfLifeEndpoints1.contains(request.getServletPath())) {
                Date endOfLife1Date = new SimpleDateFormat("yyyy-MM-dd").parse(endpointEndOfLife1);
                if (new Date().after(endOfLife1Date)) {
                    now = new DateTime(endOfLife1Date);
                }
            }

            ((MutableRequestWrapper) request).addParameter("now", now.toString(ISODateTimeFormat.dateTime()));
            User user = UserContextHolder.getUser();
            if (user == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "User not available from UserContext. Check for Auth errors");
                return false;
            }
            ((MutableRequestWrapper) request).addParameter("catchmentId", String.valueOf(userUtil.getCatchmentId()));
        }
        return true;
    }
}
