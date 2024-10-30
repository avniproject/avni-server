package org.avni.server.framework.security;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class LimitHostNamesFilterTest {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @Before
    public void setup() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @Test
    public void shouldRespondOkIfNoFiltersConfigured() throws ServletException, IOException {
        new LimitHostNamesFilter(null).doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus(), equalTo(HttpServletResponse.SC_OK));
    }

    @Test
    public void shouldRespondWithBadRequestIfHostDoesNotMatch() throws ServletException, IOException {
        request.setServerName("app.avniproject.org");
        new LimitHostNamesFilter(new String[]{"staging.avniproject.org"}).doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus(), equalTo(HttpServletResponse.SC_BAD_REQUEST));
    }

    @Test
    public void shouldRespondWithBadRequestIfServerNameNotSent() throws ServletException, IOException {
        request.setServerName(null);
        new LimitHostNamesFilter(new String[]{"staging.avniproject.org"}).doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus(), equalTo(HttpServletResponse.SC_BAD_REQUEST));
    }

    @Test
    public void shouldValidateServerName() throws ServletException, IOException {
        request.setServerName("staging.avniproject.org");
        new LimitHostNamesFilter(new String[]{"staging.avniproject.org"}).doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus(), equalTo(HttpServletResponse.SC_OK));
    }
}
