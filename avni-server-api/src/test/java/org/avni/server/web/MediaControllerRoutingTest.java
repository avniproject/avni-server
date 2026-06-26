package org.avni.server.web;

import com.amazonaws.HttpMethod;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.storage.StorageServiceProvider;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MediaControllerRoutingTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String MODEL_FILE = SHA + ".bin";
    private static final String MODEL_REL_KEY = "models/" + MODEL_FILE;
    private static final String MODEL_URL =
            "https://bucket.s3.ap-south-1.amazonaws.com/orgdir/models/" + MODEL_FILE;
    private static final String NORMAL_URL =
            "https://bucket.s3.ap-south-1.amazonaws.com/orgdir/photo.jpg";

    @Mock
    private S3Service defaultS3Service;
    @Mock
    private S3Service modelBackend;
    @Mock
    private StorageServiceProvider storageServiceProvider;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private ErrorBodyBuilder errorBodyBuilder;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private UserGroupRepository userGroupRepository;

    private MediaController controller;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        controller = new MediaController(defaultS3Service, storageServiceProvider, accessControlService,
                errorBodyBuilder, groupRepository, userGroupRepository);

        when(storageServiceProvider.forDataClass(StorageDataClass.MODEL)).thenReturn(modelBackend);
        when(storageServiceProvider.forDataClass(StorageDataClass.DEFAULT)).thenReturn(defaultS3Service);

        when(modelBackend.generateMediaDownloadUrl(anyString())).thenReturn(new URL("https://gcs/model"));
        when(defaultS3Service.generateMediaDownloadUrl(anyString())).thenReturn(new URL("https://s3/default"));
        when(modelBackend.uploadImageFile(any(), anyString())).thenReturn(new URL("https://gcs/uploaded"));
        when(defaultS3Service.uploadImageFile(any(), anyString())).thenReturn(new URL("https://s3/uploaded"));
        when(modelBackend.generateMediaUploadUrl(anyString(), any(HttpMethod.class)))
                .thenReturn(new URL("https://gcs/put"));
        when(defaultS3Service.generateMediaUploadUrl(anyString(), any(HttpMethod.class)))
                .thenReturn(new URL("https://s3/put"));
        when(modelBackend.getURLForExtensions(anyString(), any(Organisation.class)))
                .thenReturn(new URL("https://gcs/model-blob"));
        when(defaultS3Service.getURLForExtensions(anyString(), any(Organisation.class)))
                .thenReturn(new URL("https://s3/default-blob"));

        Organisation organisation = new TestOrganisationBuilder().setId(1L).build();
        User user = new User();
        user.setId(7L);
        user.setUsername("device-user@org");
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        userContext.setUser(user);
        UserContextHolder.create(userContext);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void serveOfAModelObjectSignsOnTheResolvedModelBackend() {
        ResponseEntity<String> response = controller.generateDownloadUrl(MODEL_URL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://gcs/model", response.getBody());
        verify(storageServiceProvider).forDataClass(StorageDataClass.MODEL);
        verify(modelBackend).generateMediaDownloadUrl(MODEL_URL);
        verify(defaultS3Service, never()).generateMediaDownloadUrl(anyString());
    }

    @Test
    public void serveOfANormalObjectSignsOnTheDefaultBackendUnchanged() {
        ResponseEntity<String> response = controller.generateDownloadUrl(NORMAL_URL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://s3/default", response.getBody());
        verify(storageServiceProvider).forDataClass(StorageDataClass.DEFAULT);
        verify(defaultS3Service).generateMediaDownloadUrl(NORMAL_URL);
        verify(modelBackend, never()).generateMediaDownloadUrl(anyString());
    }

    @Test
    public void uploadOfAModelObjectGoesToTheModelBackendWithTheModelsKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", MODEL_FILE, "application/octet-stream", new byte[]{1, 2, 3, 4});

        ResponseEntity<?> response = controller.uploadMedia(file, StorageDataClass.MODEL_NAMESPACE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        verify(storageServiceProvider).forDataClass(StorageDataClass.MODEL);
        verify(modelBackend).uploadImageFile(any(), eq(MODEL_REL_KEY));
        verify(defaultS3Service, never()).uploadImageFile(any(), anyString());
    }

    @Test
    public void modelUploadByANonAdminIsRejected() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.EditOrganisationConfiguration))
                .when(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        MockMultipartFile file = new MockMultipartFile(
                "file", MODEL_FILE, "application/octet-stream", new byte[]{1, 2, 3, 4});

        assertThrows(AvniAccessException.class,
                () -> controller.uploadMedia(file, StorageDataClass.MODEL_NAMESPACE));
        verify(modelBackend, never()).uploadImageFile(any(), anyString());
    }

    @Test
    public void modelUploadWithANonShaFileNameIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foo.bin", "application/octet-stream", new byte[]{1, 2, 3, 4});

        assertThrows(BadRequestError.class,
                () -> controller.uploadMedia(file, StorageDataClass.MODEL_NAMESPACE));
        verify(modelBackend, never()).uploadImageFile(any(), anyString());
    }

    @Test
    public void modelUploadWithATraversalFileNameIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../x.bin", "application/octet-stream", new byte[]{1, 2, 3, 4});

        assertThrows(BadRequestError.class,
                () -> controller.uploadMedia(file, StorageDataClass.MODEL_NAMESPACE));
        verify(modelBackend, never()).uploadImageFile(any(), anyString());
    }

    @Test
    public void uploadWithAnUnknownParentFolderIsRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        assertThrows(BadRequestError.class,
                () -> controller.uploadMedia(file, "something-else"));
        verify(modelBackend, never()).uploadImageFile(any(), anyString());
        verify(defaultS3Service, never()).uploadImageFile(any(), anyString());
    }

    @Test
    public void uploadOfANormalObjectGoesToTheDefaultBackendWithAUuidKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        ResponseEntity<?> response = controller.uploadMedia(file, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(storageServiceProvider).forDataClass(StorageDataClass.DEFAULT);
        verify(accessControlService, never()).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(defaultS3Service).uploadImageFile(any(), keyCaptor.capture());
        assertTrue("default key must keep the original extension", keyCaptor.getValue().endsWith(".jpg"));
        assertTrue("default key must NOT be in the models/ namespace",
                StorageDataClass.dataClassForKey(keyCaptor.getValue()) == StorageDataClass.DEFAULT);
        verify(modelBackend, never()).uploadImageFile(any(), anyString());
    }

    @Test
    public void presignedPutOfAModelKeyRoutesToTheModelBackend() {
        ResponseEntity<String> response = controller.generateUploadUrl(MODEL_REL_KEY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://gcs/put", response.getBody());
        verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        verify(storageServiceProvider).forDataClass(StorageDataClass.MODEL);
        verify(modelBackend).generateMediaUploadUrl(MODEL_REL_KEY, HttpMethod.PUT);
    }

    @Test
    public void presignedPutOfAModelKeyByANonAdminIsRejected() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.EditOrganisationConfiguration))
                .when(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);

        assertThrows(AvniAccessException.class, () -> controller.generateUploadUrl(MODEL_REL_KEY));
        verify(modelBackend, never()).generateMediaUploadUrl(anyString(), any(HttpMethod.class));
    }

    @Test
    public void modelBlobUrlSignsTheRelativeKeyOnTheResolvedModelBackend() {
        ResponseEntity<String> response = controller.generateModelBlobUrl(MODEL_REL_KEY);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://gcs/model-blob", response.getBody());
        verify(storageServiceProvider).forDataClass(StorageDataClass.MODEL);
        verify(modelBackend).getURLForExtensions(eq(MODEL_REL_KEY), any(Organisation.class));
        verify(defaultS3Service, never()).getURLForExtensions(anyString(), any(Organisation.class));
    }

    @Test
    public void modelBlobUrlWithANonModelsKeyIsRejected() {
        assertThrows(BadRequestError.class, () -> controller.generateModelBlobUrl("photos/" + MODEL_FILE));
        verify(modelBackend, never()).getURLForExtensions(anyString(), any(Organisation.class));
    }

    @Test
    public void modelBlobUrlWithANonHexFileNameIsRejected() {
        assertThrows(BadRequestError.class,
                () -> controller.generateModelBlobUrl("models/zzzz.bin"));
        verify(modelBackend, never()).getURLForExtensions(anyString(), any(Organisation.class));
    }

    @Test
    public void modelBlobUrlWithATraversalKeyIsRejected() {
        assertThrows(BadRequestError.class,
                () -> controller.generateModelBlobUrl("models/../" + MODEL_FILE));
        verify(modelBackend, never()).getURLForExtensions(anyString(), any(Organisation.class));
    }

    @Test
    public void presignedPutOfANormalKeyRoutesToTheDefaultBackend() {
        ResponseEntity<String> response = controller.generateUploadUrl("somefile.png");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://s3/put", response.getBody());
        verify(accessControlService, never()).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        verify(defaultS3Service).generateMediaUploadUrl("somefile.png", HttpMethod.PUT);
    }
}
