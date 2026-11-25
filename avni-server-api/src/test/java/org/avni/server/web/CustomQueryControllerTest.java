package org.avni.server.web;

import org.avni.server.dao.QueryRepository;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.CustomQueryRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.HashMap;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

public class CustomQueryControllerTest {
    @Mock
    private QueryRepository queryRepository;
    @Mock
    private AccessControlService accessControlService;

    private CustomQueryController controller;

    @Before
    public void setUp() {
        initMocks(this);
        controller = new CustomQueryController(queryRepository, accessControlService);
    }

    @Test
    public void userWithoutAnalyticsPrivilegeCannotRunASavedQuery() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.Analytics))
                .when(accessControlService).checkPrivilege(PrivilegeType.Analytics);

        try {
            controller.executeQuery(aRequest());
            fail("a user without the Analytics privilege must not reach the saved query");
        } catch (AvniAccessException expected) {
        }
        verify(queryRepository, never()).runQuery(any());
    }

    @Test
    public void userWithAnalyticsPrivilegeRunsTheSavedQuery() {
        CustomQueryRequest request = aRequest();

        controller.executeQuery(request);

        verify(accessControlService).checkPrivilege(PrivilegeType.Analytics);
        verify(queryRepository).runQuery(request);
    }

    private static CustomQueryRequest aRequest() {
        CustomQueryRequest request = new CustomQueryRequest();
        request.setName("a query");
        request.setQueryParams(new HashMap<>());
        return request;
    }
}
