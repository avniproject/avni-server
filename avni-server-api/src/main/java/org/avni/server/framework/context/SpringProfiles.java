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
    public static final String DEV = "dev";
    public static final String STAGING = "staging";
    private final Environment environment;

    @Autowired
    public SpringProfiles(Environment environment) {
        this.environment = environment;
    }

    @Deprecated
    public boolean isOnPremise() {
        return isProfile(SpringProfiles.ON_PREMISE);
    }

    /**
     * Whether the app is running under the {@code dev} or {@code staging} profile - the profiles
     * that, before avniproject/avni-server#1012, were the only ones with per-org storage selection
     * (the {@code useMinioForStorage} branch). Kept so DEFAULT-class resolution stays byte-for-byte
     * identical on those profiles after the resolver was made profile-agnostic.
     */
    public boolean isDevOrStaging() {
        return isProfile(SpringProfiles.DEV) || isProfile(SpringProfiles.STAGING);
    }

    private boolean isProfile(String profileName) {
        return Arrays.asList(environment.getActiveProfiles()).contains(profileName);
    }
}
