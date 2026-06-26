package org.avni.server.service.storage;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.S3Service;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class StorageResolverTest {

    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private StorageServiceFactory storageServiceFactory;
    @Mock
    private StorageCredentialProvider credentialProvider;

    private StorageResolver resolver;
    private Organisation organisation;

    private final S3Service defaultBackend = mock(S3Service.class, "default");
    private final S3Service modelBackend = mock(S3Service.class, "model");

    private static S3Service mock(Class<S3Service> c, String name) {
        return org.mockito.Mockito.mock(c, name);
    }

    @Before
    public void setUp() {
        initMocks(this);
        resolver = new StorageResolver(organisationConfigService, storageServiceFactory, credentialProvider);
        organisation = new Organisation("Test Org");
        organisation.setId(42L);
    }

    private OrganisationConfig configWith(Map<String, Object> backends, Map<String, Object> targets) {
        JsonObject settings = new JsonObject();
        if (backends != null) settings.put("storageBackends", backends);
        if (targets != null) settings.put("storageTargets", targets);
        OrganisationConfig config = new OrganisationConfig();
        config.setSettings(settings);
        return config;
    }

    private Map<String, Object> gcsTarget() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", "gcs");
        t.put("endpoint", "https://storage.googleapis.com");
        t.put("bucket", "org-models");
        t.put("credentialRef", "org-gcs-creds");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("org-gcs", t);
        return targets;
    }

    private Map<String, Object> modelRoutedTo(String targetName) {
        Map<String, Object> backends = new LinkedHashMap<>();
        backends.put("model", targetName);
        return backends;
    }

    @Test
    public void configuredModelResolvesToRoutedTarget() {
        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(modelRoutedTo("org-gcs"), gcsTarget()));
        when(storageServiceFactory.build(eq(organisation), any(StorageTarget.class))).thenReturn(modelBackend);

        S3Service resolved = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);

        assertSame("MODEL must resolve to the routed (built) target backend", modelBackend, resolved);

        ArgumentCaptor<StorageTarget> targetCaptor = ArgumentCaptor.forClass(StorageTarget.class);
        verify(storageServiceFactory).build(eq(organisation), targetCaptor.capture());
        StorageTarget built = targetCaptor.getValue();
        assertTrue("the built target must be the org-gcs descriptor", "org-gcs".equals(built.getName()));
        assertTrue("type must be GCS", built.getType() == StorageBackendType.GCS);
        assertTrue("bucket must come from config", "org-models".equals(built.getBucket()));
    }

    @Test
    public void unconfiguredOrgResolvesModelToDefaultBackend() {
        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(null, null));

        S3Service resolved = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);

        assertSame("unconfigured org must resolve MODEL to today's default", defaultBackend, resolved);
        verify(storageServiceFactory, never()).build(any(), any());
    }

    @Test
    public void defaultDataClassAlwaysResolvesToDefaultBackend() {
        S3Service resolved = resolver.resolve(organisation, StorageDataClass.DEFAULT, () -> defaultBackend);

        assertSame("DEFAULT class must always resolve to today's backend", defaultBackend, resolved);
        verify(organisationConfigService, never()).getOrganisationConfig(any());
        verify(storageServiceFactory, never()).build(any(), any());
    }

    @Test
    public void nullOrganisationResolvesToDefaultBackend() {
        S3Service resolved = resolver.resolve(null, StorageDataClass.MODEL, () -> defaultBackend);
        assertSame(defaultBackend, resolved);
        verify(storageServiceFactory, never()).build(any(), any());
    }

    @Test
    public void unknownTargetNameFailsLoud() {
        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(modelRoutedTo("missing"), gcsTarget()));

        try {
            resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);
            fail("an unknown routed target must fail loud, not fall back to default");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().toLowerCase().contains("missing")
                    || e.getMessage().toLowerCase().contains("storage target"));
        }
        verify(storageServiceFactory, never()).build(any(), any());
    }

    @Test
    public void modelRoutedButNoTargetsMapFailsLoud() {
        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(modelRoutedTo("org-gcs"), null));

        try {
            resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);
            fail("routing to a target with no storageTargets map must fail loud");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().contains("org-gcs") || e.getMessage().toLowerCase().contains("storagetargets"));
        }
    }

    @Test
    public void malformedTargetDescriptorFailsLoud() {
        Map<String, Object> badTarget = new LinkedHashMap<>();
        badTarget.put("type", "gcs");
        badTarget.put("endpoint", "https://storage.googleapis.com");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("org-gcs", badTarget);

        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(modelRoutedTo("org-gcs"), targets));

        try {
            resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);
            fail("a malformed target descriptor must fail loud");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().toLowerCase().contains("bucket")
                    || e.getMessage().toLowerCase().contains("org-gcs"));
        }
        verify(storageServiceFactory, never()).build(any(), any());
    }

    @Test
    public void sameVersionReusesCachedTargetServiceAndBuildsOnce() {
        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(modelRoutedTo("org-gcs"), gcsTarget()));
        when(storageServiceFactory.build(eq(organisation), any(StorageTarget.class))).thenReturn(modelBackend);
        when(credentialProvider.credentialVersion(eq(organisation), eq("org-gcs-creds"))).thenReturn(100L);

        S3Service first = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);
        S3Service second = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);

        assertSame("cached service must be reused when the version is unchanged", first, second);
        verify(storageServiceFactory, times(1)).build(eq(organisation), any(StorageTarget.class));
    }

    @Test
    public void credentialRotationInvalidatesCacheAndShutsDownPreviousClient() {
        TargetStorageService firstClient = org.mockito.Mockito.mock(TargetStorageService.class, "first");
        TargetStorageService secondClient = org.mockito.Mockito.mock(TargetStorageService.class, "second");

        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(modelRoutedTo("org-gcs"), gcsTarget()));
        when(storageServiceFactory.build(eq(organisation), any(StorageTarget.class)))
                .thenReturn(firstClient, secondClient);
        when(credentialProvider.credentialVersion(eq(organisation), eq("org-gcs-creds")))
                .thenReturn(100L, 200L);

        S3Service first = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);
        S3Service second = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);

        assertSame(firstClient, first);
        assertSame("a rotated credential must produce a freshly built client", secondClient, second);
        verify(storageServiceFactory, times(2)).build(eq(organisation), any(StorageTarget.class));
        verify(firstClient).shutdown();
    }

    @Test
    public void modelWithoutRoutingEntryResolvesToDefault() {
        Map<String, Object> backends = new LinkedHashMap<>();
        backends.put("default", "avni-s3");
        when(organisationConfigService.getOrganisationConfig(organisation))
                .thenReturn(configWith(backends, gcsTarget()));

        S3Service resolved = resolver.resolve(organisation, StorageDataClass.MODEL, () -> defaultBackend);
        assertSame(defaultBackend, resolved);
        verify(storageServiceFactory, never()).build(any(), any());
    }
}
