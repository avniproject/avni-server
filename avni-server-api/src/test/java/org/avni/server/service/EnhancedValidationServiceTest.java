package org.avni.server.service;

import com.bugsnag.Bugsnag;
import org.avni.server.common.ValidationResult;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.util.BugsnagReporter;
import org.avni.server.web.validation.ValidationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class EnhancedValidationServiceTest {
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private BugsnagReporter bugsnagReporter;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private S3Service s3Service;

    private EnhancedValidationService enhancedValidationService;

    @Before
    public void setup() {
        initMocks(this);
        enhancedValidationService = new EnhancedValidationService(formMappingService, organisationConfigService, bugsnagReporter, conceptRepository, subjectTypeRepository, individualRepository, addressLevelTypeRepository, s3Service);
    }

    @Test(expected = ValidationException.class)
    public void shouldThrowValidationExceptionForInvalidDataIfFailOnValidationIsEnabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(true);
        String errorMessage = "Dummy Error Message";
        enhancedValidationService.handleValidationFailure(errorMessage);
    }

    @Test
    public void shouldReturnValidationFailureForInvalidDataIfFailOnValidationIsDisabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(false);
        String errorMessage = "Dummy Error Message";
        ValidationResult validationResult = enhancedValidationService.handleValidationFailure(errorMessage);
        assertTrue(validationResult.isFailure());
    }
}
