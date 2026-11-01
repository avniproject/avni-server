package org.avni.server.framework.security;

import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.avni.server.framework.security.AuthTokenManager.AUTH_TOKEN;

public class AuthTokenManagerTest {

    public static final String HEADER_DUMMY_TOKEN = "dummyToken";
    public static final String QUERY_PARAM_DUMMY_TOKEN = "dummyToken2";
    public static final String QUERY_STRING_WITH_AUTH_TOKEN = AUTH_TOKEN+ QUERY_PARAM_DUMMY_TOKEN;
    public static final String EMPTY_QUERY_STRING = "";

    @Test
    public void getDerivedAuthTokenFromRequestHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthenticationFilter.AUTH_TOKEN_HEADER, HEADER_DUMMY_TOKEN);
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, EMPTY_QUERY_STRING)).isEqualTo(HEADER_DUMMY_TOKEN) ;
    }

    @Test
    public void getDerivedAuthTokenFromQueryString() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, QUERY_STRING_WITH_AUTH_TOKEN)).isEqualTo(QUERY_PARAM_DUMMY_TOKEN) ;
    }

    @Test
    public void dontObtainDerivedAuthToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, EMPTY_QUERY_STRING)).isNull();
    }

    @Test
    public void obtainDerivedAuthToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthenticationFilter.AUTH_TOKEN_HEADER, HEADER_DUMMY_TOKEN);
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, QUERY_STRING_WITH_AUTH_TOKEN)).isNotEmpty();
    }

    @Test
    public void obtainDerivedAuthTokenFromQueryStringOverridingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuthenticationFilter.AUTH_TOKEN_HEADER, HEADER_DUMMY_TOKEN);
        assertThat (AuthTokenManager.getInstance().getDerivedAuthToken(request, QUERY_STRING_WITH_AUTH_TOKEN)).isEqualTo(HEADER_DUMMY_TOKEN);
    }

    @Test
    public void maskAuthTokenLeavesNullAndEmptyQueryStringAlone() {
        assertThat(AuthTokenManager.maskAuthToken(null)).isNull();
        assertThat(AuthTokenManager.maskAuthToken(EMPTY_QUERY_STRING)).isEqualTo(EMPTY_QUERY_STRING);
    }

    @Test
    public void maskAuthTokenLeavesQueryStringWithoutTokenAlone() {
        assertThat(AuthTokenManager.maskAuthToken("page=0&size=10")).isEqualTo("page=0&size=10");
    }

    @Test
    public void maskAuthTokenWhenTokenIsTheOnlyParam() {
        assertThat(AuthTokenManager.maskAuthToken(QUERY_STRING_WITH_AUTH_TOKEN)).isEqualTo("AUTH-TOKEN=***");
    }

    @Test
    public void maskAuthTokenWhenTokenIsFirstMiddleOrLastParam() {
        assertThat(AuthTokenManager.maskAuthToken("AUTH-TOKEN=abc.def&page=0")).isEqualTo("AUTH-TOKEN=***&page=0");
        assertThat(AuthTokenManager.maskAuthToken("page=0&AUTH-TOKEN=abc.def&size=10")).isEqualTo("page=0&AUTH-TOKEN=***&size=10");
        assertThat(AuthTokenManager.maskAuthToken("page=0&size=10&AUTH-TOKEN=abc.def")).isEqualTo("page=0&size=10&AUTH-TOKEN=***");
    }

    @Test
    public void maskAuthTokenWithSemicolonSeparators() {
        assertThat(AuthTokenManager.maskAuthToken("page=0;AUTH-TOKEN=abc.def;size=10")).isEqualTo("page=0;AUTH-TOKEN=***;size=10");
        assertThat(AuthTokenManager.maskAuthToken("page=0&AUTH-TOKEN=abc.def;size=10")).isEqualTo("page=0&AUTH-TOKEN=***;size=10");
    }

    @Test
    public void maskAuthTokenDoesNotMaskParamThatOnlyEndsWithTokenName() {
        assertThat(AuthTokenManager.maskAuthToken("MY-AUTH-TOKEN=abc&page=0")).isEqualTo("MY-AUTH-TOKEN=abc&page=0");
    }
}
