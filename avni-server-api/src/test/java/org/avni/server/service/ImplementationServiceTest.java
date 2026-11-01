package org.avni.server.service;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.organisation.OrganisationCategory;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.validation.ValidationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class ImplementationServiceTest {
    @Mock
    private OrganisationService organisationService;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private UserService userService;

    private ImplementationService implementationService;
    private Organisation organisation;

    @Before
    public void setUp() {
        openMocks(this);
        implementationService = new ImplementationService(organisationService, organisationConfigService, accessControlService, userService);
        User user = new User();
        user.setId(5L);
        organisation = new Organisation();
        organisation.setCategory(category("Prototype"));
        UserContext userContext = new UserContext();
        userContext.setUser(user);
        userContext.setOrganisation(organisation);
        UserContextHolder.create(userContext);
        when(organisationService.getCurrentOrganisation()).thenReturn(organisation);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private static OrganisationCategory category(String name) {
        OrganisationCategory category = new OrganisationCategory();
        category.setName(name);
        return category;
    }

    @Test
    public void allowedDeletionPassesTheChecksAndDeletesNothing() {
        implementationService.assertCanDeleteImplementationData(true, true);

        verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        verify(accessControlService).checkPrivilege(PrivilegeType.UploadMetadataAndData);
        verify(accessControlService).checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
        verifyNoDeletion();
    }

    @Test
    public void transactionalOnlyDeletionNeedsOnlyEditOrganisationConfiguration() {
        implementationService.assertCanDeleteImplementationData(false, false);

        verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        verify(accessControlService, never()).checkPrivilege(PrivilegeType.UploadMetadataAndData);
        verify(accessControlService, never()).checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
    }

    @Test
    public void superAdminIsRefused() {
        when(userService.isAdmin(any())).thenReturn(true);
        ValidationException e = assertThrows(ValidationException.class, () -> implementationService.assertCanDeleteImplementationData(false, false));
        assertEquals("Super admin cannot delete implementation data", e.getMessage());
        verifyNoDeletion();
    }

    @Test
    public void productionAndUatOrganisationsAreRefused() {
        for (String categoryName : new String[]{OrganisationCategory.Production, OrganisationCategory.UAT}) {
            organisation.setCategory(category(categoryName));
            ValidationException e = assertThrows(ValidationException.class, () -> implementationService.assertCanDeleteImplementationData(false, false));
            assertEquals("Production or UAT organisation's data cannot be deleted", e.getMessage());
        }
        verifyNoDeletion();
    }

    @Test
    public void adminConfigWithoutMetadataIsRefused() {
        ValidationException e = assertThrows(ValidationException.class, () -> implementationService.assertCanDeleteImplementationData(false, true));
        assertEquals("You cannot delete admin config data without deleting metadata", e.getMessage());
    }

    @Test
    public void missingPrivilegeIsRefusedBeforeAnythingIsDeleted() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.UploadMetadataAndData))
                .when(accessControlService).checkPrivilege(PrivilegeType.UploadMetadataAndData);
        assertThrows(AvniAccessException.class, () -> implementationService.assertCanDeleteImplementationData(true, false));
        verifyNoDeletion();
    }

    private void verifyNoDeletion() {
        verify(organisationService, never()).deleteTransactionalData(any());
        verify(organisationService, never()).deleteMediaContent(anyBoolean());
        verify(organisationService, never()).deleteMetadata(any());
        verify(organisationService, never()).deleteAdminConfigData(any());
    }
}
