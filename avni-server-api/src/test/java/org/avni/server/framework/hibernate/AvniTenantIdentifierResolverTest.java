package org.avni.server.framework.hibernate;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.After;
import org.junit.Test;

import static org.avni.server.framework.hibernate.AvniTenantIdentifierResolver.SUPER_ADMIN;
import static org.junit.Assert.assertEquals;

/**
 * Pins every branch of the #1009 cache-key resolver, in particular the admin-without-org branch that
 * routes to SUPER_ADMIN (the one the integration test cannot reach, since regular org users are never
 * admins). Guards against the predicate drifting out of lockstep with SetOrganisationJdbcInterceptor's
 * RLS-bypass check - both now delegate to UserContext.isActingAsSuperAdmin().
 */
public class AvniTenantIdentifierResolverTest {
    private final AvniTenantIdentifierResolver resolver = new AvniTenantIdentifierResolver();

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private void setContext(User user, Organisation organisation, String organisationUUID) {
        UserContext userContext = new UserContext();
        if (user != null) {
            userContext.setUser(user);
        }
        userContext.setOrganisation(organisation);
        userContext.setOrganisationUUID(organisationUUID);
        UserContextHolder.create(userContext);
    }

    @Test
    public void noUserContextResolvesToSuperAdmin() {
        UserContextHolder.clear();
        assertEquals(SUPER_ADMIN, resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    public void regularUserResolvesToOwnOrgId() {
        Organisation org = new TestOrganisationBuilder().setId(42L).build();
        User user = new UserBuilder().id(1L).userName("regular").organisationId(42L).build();
        setContext(user, org, "org-a-uuid");
        assertEquals("42", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    public void adminWithoutSelectedOrgResolvesToSuperAdmin() {
        // RLS-bypass mode: admin acting with no organisationUUID, even though a home org is set. This is
        // the branch this fix added - it must NOT bucket under the home org id (id 7) or it would poison
        // that org's cache with the cross-org rows the bypassed connection can read.
        Organisation homeOrg = new TestOrganisationBuilder().setId(7L).build();
        User admin = new UserBuilder().id(2L).userName("admin").isAdmin(true).organisationId(7L).build();
        setContext(admin, homeOrg, null);
        assertEquals(SUPER_ADMIN, resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    public void adminWithSelectedOrgResolvesToThatOrgId() {
        // An admin who selected an org (organisationUUID set) is RLS-scoped to it, so it gets that org's bucket.
        Organisation selectedOrg = new TestOrganisationBuilder().setId(9L).build();
        User admin = new UserBuilder().id(3L).userName("admin").isAdmin(true).organisationId(7L).build();
        setContext(admin, selectedOrg, "selected-org-uuid");
        assertEquals("9", resolver.resolveCurrentTenantIdentifier());
    }

    @Test
    public void nonAdminWithoutOrganisationResolvesToSuperAdmin() {
        User user = new UserBuilder().id(4L).userName("orphan").build();
        setContext(user, null, null);
        assertEquals(SUPER_ADMIN, resolver.resolveCurrentTenantIdentifier());
    }
}
