package org.avni.server.service.abha;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// The Eka environments this server may use: the deployment value avni.eka.allowedEnvironments, a comma-separated list,
// production unless a deployment says otherwise, so a production server is never pointed at Eka's sandbox. Settings for
// another environment are refused on save (AbhaAdminService), and never logged in with (EkaTokenService).
@Component
public class EkaEnvironments {
    private final Set<String> allowed;

    public EkaEnvironments(@Value("${avni.eka.allowedEnvironments:production}") String allowedEnvironments) {
        this.allowed = allowedEnvironments == null ? Set.of() : Arrays.stream(allowedEnvironments.split(","))
                .map(String::trim).filter(name -> !name.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String environment) {
        return environment != null && allowed.contains(environment);
    }

    public String describe() {
        return allowed.stream().sorted().collect(Collectors.joining(", "));
    }
}
