package org.avni.server.framework.security;

import org.avni.server.config.IdpType;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity(debug = false)
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiSecurity {
    private final AuthService authService;

    @Value("${avni.defaultUserName}")
    private String defaultUserName;

    @Value("${avni.idp.type}")
    private IdpType idpType;

    @Value("${avni.blacklisted.urls-file}")
    private String avniBlacklistedUrlsFile;

    @Value("${avni.csrf.enabled}")
    private boolean csrfEnabled;

    @Value("${csp.allowed.hosts}")
    private String cspAllowedHosts;
    private final ErrorBodyBuilder errorBodyBuilder;

    @Value("${csp.enabled}")
    private boolean cspEnabled;

    @Autowired
    public ApiSecurity(AuthService authService, ErrorBodyBuilder errorBodyBuilder) {
        this.authService = authService;
        this.errorBodyBuilder = errorBodyBuilder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        /*
         * Refer the following documents for CSP
         * https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP
         * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy
         * https://developer.mozilla.org/en-US/docs/Glossary/Fetch_directive
         */
        if (cspEnabled) {
            String policyDirectives = "default-src 'self' ; connect-src 'self' " + cspAllowedHosts + ";";
            policyDirectives += "img-src 'self' " + cspAllowedHosts + " data: ;";
            policyDirectives += "style-src 'self' 'unsafe-inline'; object-src 'none';";
            policyDirectives += "script-src 'self' 'unsafe-inline' 'unsafe-eval'";
            policyDirectives += " 'sha256-5As4+3YpY62+l38PsxCEkjB1R4YtyktBtRScTJ3fyLU=' ";
            policyDirectives += " 'sha256-MDtIDJhP1FMu16GoPm7X/I7sEECznvKCwlPRG8uDDDc=' ;";
            http.headers().xssProtection().and().contentSecurityPolicy(policyDirectives);
        } else {
            http.headers().xssProtection();
        }

        CsrfConfigurer<HttpSecurity> csrf = http.headers().frameOptions().sameOrigin().and().csrf();
        HttpSecurity httpSecurity;
        if (csrfEnabled)
            httpSecurity = csrf.ignoringRequestMatchers("/api/**").csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).and();
        else
            httpSecurity = csrf.disable();

        httpSecurity
                .formLogin().disable()
                .httpBasic().disable()
                .authorizeRequests().anyRequest().permitAll()
                .and()
                .addFilter(new AuthenticationFilter(authService, idpType, defaultUserName, avniBlacklistedUrlsFile, errorBodyBuilder))
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
