package org.avni.server.util;

import org.avni.server.domain.Account;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;

public class RegionUtil {
    public static String getCurrentUserRegion() {
        Organisation organisation = UserContextHolder.getOrganisation();
        User user = UserContextHolder.getUser();
        if (organisation != null) {
            return organisation.getAccount().getRegion();
        }
        if (user != null && user.isAdmin()) {
            return Account.DEFAULT_REGION;
        }
        throw new RuntimeException("Could not determine region");
    }
}
