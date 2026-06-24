package org.avni.server.framework.hibernate;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resolves the current tenant (organisation) identifier for Hibernate's
 * second-level cache.
 *
 * The cache key for an entity normally is {@code (entityClass, id)}, which
 * is shared across orgs. When this resolver is registered via
 * {@code spring.jpa.properties.hibernate.tenant_identifier_resolver}, the
 * cache key becomes {@code (entityClass, tenantId, id)} — different orgs
 * land in different cache slots, so a cache hit in one org cannot serve
 * another org's data.
 *
 * For super-admin code paths where no organisation is set in
 * {@link UserContextHolder}, this resolver returns {@link #SUPER_ADMIN}
 * so those calls share a separate cache bucket isolated from any real org.
 */
public class AvniTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String SUPER_ADMIN = "_super_admin_";

    @Override
    public String resolveCurrentTenantIdentifier() {
        UserContext userContext = UserContextHolder.getUserContext();
        if (userContext == null) {
            return SUPER_ADMIN;
        }
        // Mirror SetOrganisationJdbcInterceptor's RLS-bypass predicate: an admin acting without a
        // selected organisation runs unrestricted (no SET ROLE on the connection) and can read every
        // org's rows. It must share the isolated super-admin cache bucket, not its own home org's, or
        // it would poison that bucket with rows a regular home-org user must not be served from cache.
        User user = userContext.getUser();
        if (user != null && user.isAdmin() && userContext.getOrganisationUUID() == null) {
            return SUPER_ADMIN;
        }
        Organisation organisation = userContext.getOrganisation();
        if (organisation == null || organisation.getId() == null) {
            return SUPER_ADMIN;
        }
        return String.valueOf(organisation.getId());
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    // isRoot(T tenantId) is intentionally NOT overridden (Hibernate default returns false).
    // Returning true for SUPER_ADMIN would mark super-admin sessions as cross-tenant root
    // sessions, which may bypass tenant scoping in cache keys for those sessions — defeating
    // the security guarantee for super-admin code paths. The default isolates SUPER_ADMIN
    // into its own cache bucket, which is what we want.
}
