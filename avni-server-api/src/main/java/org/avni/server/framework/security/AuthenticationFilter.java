package org.avni.server.framework.security;


import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.config.AvniKeycloakConfig;
import org.avni.server.config.IdpType;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.AvniNoUserSessionException;
import org.avni.server.util.FileUtil;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.util.StringUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static org.avni.server.framework.security.ResourceProtectionStatus.isProtected;

public class AuthenticationFilter extends BasicAuthenticationFilter {
    public static final String USER_NAME_HEADER = "USER-NAME";
    public static final String AUTH_TOKEN_HEADER = "AUTH-TOKEN";
    public static final String ORGANISATION_UUID = "ORGANISATION-UUID";
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final AuthService authService;
    private final String defaultUserName;
    private final IdpType idpType;
    private final List<String> blacklistedUrls;
    private final ErrorBodyBuilder errorBodyBuilder;
    private final AvniKeycloakConfig avniKeycloakConfig;

    public AuthenticationFilter(AuthenticationManager authenticationManager, AuthService authService, IdpType idpType, String defaultUserName, String avniBlacklistedUrlsFile, ErrorBodyBuilder errorBodyBuilder, AvniKeycloakConfig avniKeycloakConfig) throws IOException {
        super(authenticationManager);
        this.authService = authService;
        this.idpType = idpType;
        this.defaultUserName = defaultUserName;
        this.errorBodyBuilder = errorBodyBuilder;
        this.avniKeycloakConfig = avniKeycloakConfig;

        String content = FileUtil.readStringOfFileOnFileSystem(avniBlacklistedUrlsFile);
        blacklistedUrls = ObjectMapperSingleton.getObjectMapper().readValue(content == null ? "[]" : content, new TypeReference<List<String>>() {
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            String username = request.getHeader(USER_NAME_HEADER);
            String organisationUUID = request.getHeader(ORGANISATION_UUID);
            String method = request.getMethod();
            String requestURI = request.getRequestURI();
            String queryString = request.getQueryString();

            logger.info(String.format("Received request %s %s?%s", method, requestURI, queryString));

            AuthTokenManager authTokenManager = AuthTokenManager.getInstance();
            boolean isProtected = isProtected(request);
            if (ResourceProtectionStatus.isPresentIn(request, blacklistedUrls)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, String.format("%s is blacklisted for the implementation", request.getServletPath()));
            } else if (isProtected) {
                String derivedAuthToken = authTokenManager.getDerivedAuthToken(request, queryString, avniKeycloakConfig.getPrivateKey());
                UserContext userContext = idpType.equals(IdpType.none)
                        ? authService.authenticateByUserName(StringUtils.isEmpty(username) ? defaultUserName : username, organisationUUID)
                        : authService.authenticateByToken(derivedAuthToken, organisationUUID);
                authTokenManager.setAuthCookie(request, response, derivedAuthToken);
                long start = System.currentTimeMillis();
                chain.doFilter(request, response);
                long end = System.currentTimeMillis();
                logger.info(String.format("%s %s?%s User: %s Organisation: %s Time: %s ms", method, requestURI, queryString, userContext.getUserName(), userContext.getOrganisationName(), (end - start)));
            } else {
                String derivedAuthToken = authTokenManager.getDerivedAuthToken(request, queryString, avniKeycloakConfig.getPrivateKey());
                authTokenManager.setAuthCookie(request, response, derivedAuthToken);
                if (!idpType.equals(IdpType.none))
                    authService.tryAuthenticateByToken(derivedAuthToken, organisationUUID);
                chain.doFilter(request, response);
            }
        } catch (AvniNoUserSessionException noUserSessionException) {
            this.logException(request, noUserSessionException);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, errorBodyBuilder.getErrorBody(noUserSessionException));
        } catch (AvniAccessException accessException) {
            this.logException(request, accessException);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, errorBodyBuilder.getErrorBody(accessException));
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
