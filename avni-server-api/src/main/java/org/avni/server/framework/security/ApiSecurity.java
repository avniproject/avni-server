package org.avni.server.framework.security;

import org.avni.server.config.IdpType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity(debug = false)
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiSecurity extends WebSecurityConfigurerAdapter {
    private final AuthService authService;

    @Value("${avni.defaultUserName}")
    private String defaultUserName;

    @Value("${avni.idp.type}")
    private IdpType idpType;

    @Value("${avni.blacklisted.urls-file}")
    private String avniBlacklistedUrlsFile;

    @Value("${avni.csrf.enabled}")
    private boolean csrfEnabled;

    @Autowired
    public ApiSecurity(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        CsrfConfigurer<HttpSecurity> csrf = http.cors().and().csrf();
        HttpSecurity httpSecurity;
        if (csrfEnabled)
            httpSecurity = csrf.ignoringAntMatchers("/api/**").csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).and();
        else
            httpSecurity = csrf.disable();

        httpSecurity
                .formLogin().disable()
                .httpBasic().disable()
                .authorizeRequests().anyRequest().permitAll()
                .and()
                .addFilter(new AuthenticationFilter(authenticationManager(), authService, idpType, defaultUserName, avniBlacklistedUrlsFile))
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}
