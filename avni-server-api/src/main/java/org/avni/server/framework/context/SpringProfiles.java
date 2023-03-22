package org.avni.server.framework.context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class SpringProfiles {
    public static final String DEFAULT = "default";
    public static final String ON_PREMISE = "onPremise";
    private final Environment environment;

    @Autowired
    public SpringProfiles(Environment environment) {
        this.environment = environment;
    }

    @Deprecated
    public boolean isOnPremise() {
        return isProfile(SpringProfiles.ON_PREMISE);
    }

    private boolean isProfile(String profileName) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profileName);
    }
}
