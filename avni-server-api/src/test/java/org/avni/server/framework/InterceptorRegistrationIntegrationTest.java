package org.avni.server.framework;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InterceptorRegistrationIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private List<HandlerMapping> handlerMappings;

    @Test
    public void noAvniInterceptorRunsOnARequestOutsideItsPathPatterns() throws Exception {
        List<String> avniInterceptorsOnPing = new ArrayList<>();
        boolean pingHandled = false;
        for (HandlerMapping handlerMapping : handlerMappings) {
            HandlerExecutionChain chain = handlerMapping.getHandler(new MockHttpServletRequest("GET", "/ping"));
            if (chain == null) continue;
            pingHandled = true;
            for (HandlerInterceptor interceptor : chain.getInterceptorList()) {
                if (interceptor.getClass().getName().startsWith("org.avni.")) {
                    avniInterceptorsOnPing.add(interceptor.getClass().getName());
                }
            }
        }
        assertTrue("No handler mapping handled /ping, so this test checks nothing", pingHandled);
        assertEquals("An interceptor registered without path patterns runs on every request", new ArrayList<>(), avniInterceptorsOnPing);
    }
}
