package org.avni.server.framework.security;


import org.avni.server.config.IdpType;
import org.avni.server.domain.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class AuthenticationFilter extends BasicAuthenticationFilter {
    public static final String USER_NAME_HEADER = "USER-NAME";
    public static final String AUTH_TOKEN_HEADER = "AUTH-TOKEN";
    public static final String ORGANISATION_UUID = "ORGANISATION-UUID";
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private static final List<String> UnprotectedResources = Arrays.asList("/cognito-details", "/idp-details");

    private final AuthService authService;
    private final String defaultUserName;
    private final IdpType idpType;

    public AuthenticationFilter(AuthenticationManager authenticationManager, AuthService authService, IdpType idpType, String defaultUserName) {
        super(authenticationManager);
        this.authService = authService;
        this.idpType = idpType;
        this.defaultUserName = defaultUserName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            String username = request.getHeader(USER_NAME_HEADER);
            String organisationUUID = request.getHeader(ORGANISATION_UUID);
            String method = request.getMethod();
            String requestURI = request.getRequestURI();
            String queryString = request.getQueryString();
            AuthTokenManager authTokenManager = AuthTokenManager.getInstance();
            String derivedAuthToken = UnprotectedResources.contains(requestURI) ? null : authTokenManager.getDerivedAuthToken(request, queryString);
            UserContext userContext = idpType.equals(IdpType.none)
                        ? authService.authenticateByUserName(StringUtils.isEmpty(username) ? defaultUserName : username, organisationUUID)
                        : authService.authenticateByToken(derivedAuthToken, organisationUUID);
            authTokenManager.setAuthCookie(request, response, derivedAuthToken);
            long start = System.currentTimeMillis();
            chain.doFilter(request, response);
            long end = System.currentTimeMillis();
            logger.info(String.format("%s %s?%s User: %s Organisation: %s Time: %s ms", method, requestURI, queryString, userContext.getUserName(), userContext.getOrganisationName(), (end - start)));
        } catch (Exception exception) {
            this.logException(request, exception);
            throw exception;
        } finally {
            UserContextHolder.clear();
        }
    }

    private void logException(HttpServletRequest request, Exception exception) {
        logger.error("Exception on Request URI", request.getRequestURI());
        logger.error("Exception Message:", exception);
    }
}
