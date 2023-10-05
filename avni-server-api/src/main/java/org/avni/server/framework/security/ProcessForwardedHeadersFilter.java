package org.avni.server.framework.security;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Component
@Order(1)
// https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html
public class ProcessForwardedHeadersFilter extends ForwardedHeaderFilter {
// This filter needs to be called before LimitHostNamesFilter
// because the result of this filter is used by LimitHostNamesFilter
// when it calls request.getServerName() which returns the value set by this filter
    public ProcessForwardedHeadersFilter() {
//        setRemoveOnly(true);  //setting this to true removes the X-Forwarded headers.
    }
}
