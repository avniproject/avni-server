package org.avni.server.web;

import org.avni.server.domain.User;
import org.avni.server.service.FastSyncKeyService;
import org.avni.server.service.ResetSyncService;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlServiceStub;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.avni.server.dao.CatchmentRepository;
import org.avni.server.domain.Catchment;
import org.avni.server.web.request.CatchmentContract;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CatchmentControllerUnitTest {
    @Mock
    private CatchmentRepository catchmentRepository;
    @Mock
    private ResetSyncService resetSyncService;
    @Mock
    private S3Service s3Service;
    private CatchmentController catchmentController;

    private static final String CATCHMENT_UUID = "1f6e3a0e-0a1e-4b2b-9d6c-0f6f3a0e1111";
    private static final String REALM_KEY = "MobileDbBackup-" + CATCHMENT_UUID;
    private static final String SQLITE_KEY = "MobileDbBackupSqlite-" + CATCHMENT_UUID;

    @Before
    public void setup() {
        initMocks(this);
        catchmentController = new CatchmentController(catchmentRepository, null, null, s3Service, resetSyncService, new AccessControlServiceStub(), ErrorBodyBuilder.createForTest());
    }

    private Catchment anExistingCatchment() {
        Catchment catchment = new Catchment();
        catchment.setId(1L);
        catchment.setName("foo");
        catchment.setUuid(CATCHMENT_UUID);
        User auditUser = new User();
        auditUser.setUsername("admin@org");
        catchment.setCreatedBy(auditUser);
        catchment.setLastModifiedBy(auditUser);
        when(catchmentRepository.findOne(1L)).thenReturn(catchment);
        when(catchmentRepository.findByName("foo")).thenReturn(catchment);
        return catchment;
    }

    private CatchmentContract anUpdateOf(Catchment catchment, boolean fastSyncExists, boolean deleteFastSync) {
        CatchmentContract contract = new CatchmentContract();
        contract.setId(catchment.getId());
        contract.setName(catchment.getName());
        contract.setLocationIds(new ArrayList<>());
        contract.setFastSyncExists(fastSyncExists);
        contract.setDeleteFastSync(deleteFastSync);
        return contract;
    }

    @Test
    public void bothCatchmentDumpKeysAreTheOnesTheMediaRoutesUse() {
        assertThat(FastSyncKeyService.realmCatchmentKey(CATCHMENT_UUID), is(equalTo(REALM_KEY)));
        assertThat(FastSyncKeyService.sqliteCatchmentKey(CATCHMENT_UUID), is(equalTo(SQLITE_KEY)));
    }

    @Test
    public void adminScreenSeesAFastSyncWhenOnlyTheSqliteDumpExists() {
        anExistingCatchment();
        when(s3Service.fileExists(REALM_KEY)).thenReturn(false);
        when(s3Service.fileExists(SQLITE_KEY)).thenReturn(true);

        EntityModel<CatchmentContract> response = catchmentController.getById(1L);

        assertThat(response.getContent().isFastSyncExists(), is(true));
    }

    @Test
    public void adminScreenSeesAFastSyncWhenOnlyTheRealmDumpExists() {
        anExistingCatchment();
        when(s3Service.fileExists(REALM_KEY)).thenReturn(true);
        when(s3Service.fileExists(SQLITE_KEY)).thenReturn(false);

        EntityModel<CatchmentContract> response = catchmentController.getById(1L);

        assertThat(response.getContent().isFastSyncExists(), is(true));
    }

    @Test
    public void adminScreenSeesNoFastSyncWhenNeitherDumpExists() {
        anExistingCatchment();
        when(s3Service.fileExists(anyString())).thenReturn(false);

        EntityModel<CatchmentContract> response = catchmentController.getById(1L);

        assertThat(response.getContent().isFastSyncExists(), is(false));
    }

    @Test
    public void tickingDeleteFastSyncRemovesTheSqliteDumpAsWellAsTheRealmOne() throws Exception {
        Catchment catchment = anExistingCatchment();

        ResponseEntity<?> response = catchmentController.updateCatchment(1L, anUpdateOf(catchment, true, true));

        assertThat(response.getStatusCodeValue(), is(equalTo(200)));
        verify(s3Service).deleteObject(SQLITE_KEY);
        verify(s3Service).deleteObject(REALM_KEY);
    }

    @Test
    public void notTickingDeleteFastSyncRemovesNeitherDump() throws Exception {
        Catchment catchment = anExistingCatchment();

        ResponseEntity<?> response = catchmentController.updateCatchment(1L, anUpdateOf(catchment, true, false));

        assertThat(response.getStatusCodeValue(), is(equalTo(200)));
        verify(s3Service, never()).deleteObject(anyString());
    }

    @Test
    public void anAdminScreenThatSawNoFastSyncRemovesNeitherDump() throws Exception {
        Catchment catchment = anExistingCatchment();

        ResponseEntity<?> response = catchmentController.updateCatchment(1L, anUpdateOf(catchment, false, true));

        assertThat(response.getStatusCodeValue(), is(equalTo(200)));
        verify(s3Service, never()).deleteObject(anyString());
    }

    @Test
    public void theCatchmentIsSavedBeforeEitherDumpIsRemoved() throws Exception {
        Catchment catchment = anExistingCatchment();

        catchmentController.updateCatchment(1L, anUpdateOf(catchment, true, true));

        InOrder order = inOrder(catchmentRepository, s3Service);
        order.verify(catchmentRepository).save(catchment);
        order.verify(s3Service, atLeastOnce()).deleteObject(anyString());
    }

    @Test()
    public void shouldReturnErrorWhenOnUpdateThereAlreadyExistsACatchmentWithSameName() throws Exception {
        Catchment foo = new Catchment();
        foo.setId(1L);
        foo.setName("foo");
        Catchment bar = new Catchment();
        bar.setId(2L);
        bar.setName("bar");
        when(catchmentRepository.findOne(1L)).thenReturn(foo);
        when(catchmentRepository.findByName("bar")).thenReturn(bar);

        CatchmentContract updateCatchment = new CatchmentContract();
        updateCatchment.setId(1L);
        updateCatchment.setName("bar");

        ResponseEntity responseEntity = catchmentController.updateCatchment(1L, updateCatchment);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(400)));
        Map body = (Map) responseEntity.getBody();
        assertThat(body.get("message"), is(equalTo("Catchment with name bar already exists")));
    }

    @Test()
    public void shouldAllowToChangeCatchmentNameWhenThereIsNoConflict() throws Exception {
        Catchment foo = new Catchment();
        foo.setId(1L);
        foo.setName("foo");
        Catchment bar = new Catchment();
        bar.setId(2L);
        bar.setName("bar");
        when(catchmentRepository.findOne(1L)).thenReturn(foo);
        when(catchmentRepository.findByName("foo")).thenReturn(foo);
        when(catchmentRepository.findByName("bar")).thenReturn(bar);
        when(catchmentRepository.findByName("tada")).thenReturn(null);


        CatchmentContract updateCatchment = new CatchmentContract();
        updateCatchment.setId(1L);
        updateCatchment.setName("tada");
        updateCatchment.setLocationIds(new ArrayList<>());

        ResponseEntity responseEntity = catchmentController.updateCatchment(1L, updateCatchment);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(200)));
    }

    @Test()
    public void shouldReturnErrorWhenOnCreateThereAlreadyExistsACatchmentWithSameName() throws Exception {
        Catchment foo = new Catchment();
        foo.setId(1L);
        foo.setName("foo");
        when(catchmentRepository.findByName("foo")).thenReturn(foo);

        CatchmentContract createCatchment = new CatchmentContract();
        createCatchment.setId(2L);
        createCatchment.setName("foo");

        ResponseEntity responseEntity = catchmentController.createSingleCatchment(createCatchment);
        assertThat(responseEntity.getStatusCodeValue(), is(equalTo(400)));
        Map body = (Map) responseEntity.getBody();
        assertThat(body.get("message"), is(equalTo("Catchment with name foo already exists")));
    }
}
