package org.avni.server.framework.security;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.avni.server.framework.security.ResourceProtectionStatus.isProtected;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class ResourceProtectionStatusTest {
    @Test
    public void shouldProvideTheCorrectResourceProtectionStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cognito-details");
        request.setServletPath("/cognito-details");
        assertThat(isProtected(request), is(equalTo(false)));

        request.setServletPath("/index.html");
        assertThat(isProtected(request), is(equalTo(false)));

        request.setServletPath("/icons/myIcon/myIcon.jpeg");
        assertThat(isProtected(request), is(equalTo(false)));

        request.setServletPath("/bulkuploads/do.md");
        assertThat(isProtected(request), is(equalTo(false)));

        request.setServletPath("/userInfo");
        assertThat(isProtected(request), is(equalTo(true)));

        request.setServletPath("/web/userInfo");
        assertThat(isProtected(request), is(equalTo(true)));
    }

    @Test
    public void isPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cognito-details");
        request.setServletPath("/web/identifierUserAssignment/1");
        assertThat(ResourceProtectionStatus.isPresentIn(request, Collections.singletonList("/web/identifierUserAssignment/*")), is(equalTo(true)));
    }
}
