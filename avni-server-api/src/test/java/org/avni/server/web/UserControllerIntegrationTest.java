package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
import org.avni.server.dao.externalSystem.ExternalSystemConfigRepository;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.domain.extenalSystem.ExternalSystemConfig;
import org.avni.server.domain.extenalSystem.SystemName;
import org.junit.After;
import org.avni.server.domain.*;
import org.avni.server.web.request.UserContract;
import org.avni.server.domain.factory.UserBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;
import java.util.List;
import static org.junit.Assert.*;

@Sql({"/test-data.sql"})
public class UserControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private CatchmentRepository catchmentRepository;
    @Autowired
    private ExternalSystemConfigRepository externalSystemConfigRepository;
    private Organisation testOrganisation;
    private Catchment testCatchment;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
        testOrganisation = organisationRepository.findOne(2L);
        testCatchment = catchmentRepository.findOne(2L);
    }

    @Test
    public void shouldCreateUserWithValidData(){
        UserContract userContract = createValidUserContract("newuser@test", "New User");
        String response = postForBody(base.toString() + "/user", userContract);
        User savedUser = userRepository.findByUsername("newuser@test");
        assertNotNull("User should be created but was null. Response: " + response, savedUser);
        assertEquals("newuser@test", savedUser.getUsername());
        assertEquals("New User", savedUser.getName());
    }

    @Test
    public void shouldReturnBadRequestForNoUsername(){
        UserContract userContract = createValidUserContract(null, "Test User");
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestForEmptyUsername(){
        UserContract userContract = createValidUserContract("", "Test User");
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        UserContract userContract2 = createValidUserContract(" ", "Test User");
        ResponseEntity<String> response2 = template.postForEntity(base.toString() + "/user", userContract2, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response2.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestForDuplicateUsername(){
        UserContract userContract1 = createValidUserContract("demo-admin", "User One");
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract1, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestForInvalidEmail(){
        UserContract userContract = createValidUserContract("validuser@test", "Test User");
        userContract.setEmail("invalid-email");

        String response = postForBody(base.toString() + "/user", userContract);
        assertTrue(response.contains("Invalid email address"));
    }

    @Test
    public void shouldReturnBadRequestForInvalidName(){
        UserContract userContract = createValidUserContract("", "Valid Name");
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestForInvalidPhoneNumber(){
        UserContract userContract = createValidUserContract("validuser@test", "Valid User");
        userContract.setPhoneNumber("1111111111");
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void shouldReturnAccessDeniedForUserCreationWithoutPrivilege(){
        setUser("demouser@demo");
        UserContract userContract = createValidUserContract("accesstest@test", "Access Test User");
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void shouldGetUserById(){
        String response = template.getForObject(base.toString() + "/user/" + 2L, String.class);
        assertTrue(response.contains("demo-admin"));
    }

    @Test
    public void shouldReturnAccessDeniedForUserRetrievalWithoutPrivilege(){
        setUser("demouser@demo");
        ResponseEntity<String> response = template.getForEntity(base.toString() + "/user/" + 10L, String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void shouldReturnNotFoundForNonExistentUser(){
        ResponseEntity<String> response = template.getForEntity(base.toString() + "/user/99", String.class);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void shouldUpdateUserWithValidData(){
        User demoAdmin = userRepository.findOne(10L);

        // UserContract.fromEntity(demoAdmin) cant be used due to serialization issue
        UserContract updateContract = createValidUserContract(demoAdmin.getUsername(), "Updated Name");
        updateContract.setId(demoAdmin.getId());
        updateContract.setEmail("updated@test.com");

        template.put(base.toString() + "/user/" + 10L, updateContract);

        User updatedUser = userRepository.findOne(10L);
        assertEquals("Updated Name", updatedUser.getName());
        assertEquals("updated@test.com", updatedUser.getEmail());
    }

    @Test
    public void shouldReturnAccessDeniedForUserUpdateWithoutPrivilege(){
        setUser("demouser@demo");
        ResponseEntity<String> response = template.exchange(
            base.toString() + "/user/" + 10L,
            HttpMethod.PUT,
            new HttpEntity<>(createValidUserContract("updateaccess@test", "Updated Name")),
            String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestForUpdateWithNonExistentUsername(){
        UserContract updateContract = createValidUserContract("nonexistent@test", "Test User");
        ResponseEntity<String> response = template.exchange(
            base.toString() + "/user/1",
            HttpMethod.PUT,
            new HttpEntity<>(updateContract),
            String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void shouldDeleteUser(){
        template.delete(base.toString() + "/user/" + 10L);
        User deletedUser = userRepository.findOne(10L);
        assertTrue(deletedUser.isVoided());
    }

    @Test
    public void shouldReturnAccessDeniedForUserDeleteWithoutPrivilege(){
        setUser("demouser@demo");
        User testUser = createTestUser("deleteaccess@test", "Delete Access Test");
        ResponseEntity<String> response = template.exchange(
            base.toString() + "/user/" + testUser.getId(),
            HttpMethod.DELETE,
            null,
            String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void shouldReturnBadRequestForUpdateWithInvalidData(){
        User testUser = createTestUser("updateinvalid@test", "Update Invalid Test");
        
        UserContract updateContract = createValidUserContract(testUser.getUsername(), "Update Invalid Test");
        updateContract.setId(testUser.getId());
        updateContract.setEmail("invalid-email");
        
        ResponseEntity<String> response = template.exchange(
            base.toString() + "/user/" + testUser.getId(),
            HttpMethod.PUT,
            new HttpEntity<>(updateContract),
            String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void shouldCreateUserWithEmptyGroupIds(){
        UserContract userContract = createValidUserContract("nullgrouptest@test", "Null Group Test");
        userContract.setGroupIds(null);
        
        ResponseEntity<String> response = template.postForEntity(base.toString() + "/user", userContract, String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        User savedUser = userRepository.findByUsername("nullgrouptest@test");
        assertNotNull("User should be created without NPE", savedUser);
    }

    @Test
    public void shouldUpdateUserWithEmptyGroupIds(){
        User demoUser = userRepository.findOne(10L);
        
        UserContract updateContract = createValidUserContract(demoUser.getUsername(), "Updated Name");
        updateContract.setId(demoUser.getId());
        updateContract.setGroupIds(List.of());
        
        ResponseEntity<String> response = template.exchange(
            base.toString() + "/user/" + 10L,
            HttpMethod.PUT,
            new HttpEntity<>(updateContract),
            String.class);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        
        User updatedUser = userRepository.findOne(10L);
        assertEquals("Updated Name", updatedUser.getName());
    }


    @After
    public void removeEkaSettings() {
        ExternalSystemConfig row = externalSystemConfigRepository.findBySystemNameAndOrganisationId(SystemName.Eka, 2L);
        if (row != null) externalSystemConfigRepository.delete(row);
    }

    private void demoHasEkaHealthCentres(String... hipIds) {
        ExternalSystemConfig row = new ExternalSystemConfig();
        row.setSystemName(SystemName.Eka);
        row.assignUUIDIfRequired();
        row.setOrganisationId(2L);
        row.setConfig(new JsonObject().with(EkaSystemConfig.HIP_IDS, java.util.Arrays.asList(hipIds)));
        externalSystemConfigRepository.save(row);
    }

    private void demoAdminHasHealthCentre(String hipId) {
        User demoAdmin = userRepository.findByUsername("demo-admin");
        demoAdmin.setSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, hipId).with(UserSettings.LOCALE, "en"));
        userRepository.save(demoAdmin);
    }

    private ResponseEntity<String> putDemoAdminWithSettings(JsonObject settings) {
        UserContract contract = createValidUserContract("demo-admin", "Demo Admin");
        contract.setId(userRepository.findByUsername("demo-admin").getId());
        contract.setEmail("demo-admin@test.com");
        contract.setSettings(settings);
        return template.exchange(base + "/user/" + contract.getId(), HttpMethod.PUT, new HttpEntity<>(contract), String.class);
    }

    private String demoAdminHipId() {
        return userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.ABHA_HIP_ID);
    }

    @Test
    public void adminSaveWithoutAbhaHipIdKeepsIt() {
        demoHasEkaHealthCentres("hip-1");
        demoAdminHasHealthCentre("hip-1");

        ResponseEntity<String> response = putDemoAdminWithSettings(new JsonObject().with(UserSettings.LOCALE, "hi"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("hip-1", demoAdminHipId());
        assertEquals("hi", userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.LOCALE));
    }

    @Test
    public void adminSaveWithNewAbhaHipIdReplacesIt() {
        demoHasEkaHealthCentres("hip-1", "hip-2");
        demoAdminHasHealthCentre("hip-1");

        ResponseEntity<String> response = putDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-2"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("hip-2", demoAdminHipId());
    }

    @Test
    public void adminSaveWithEmptyAbhaHipIdClearsIt() {
        demoHasEkaHealthCentres("hip-1");
        demoAdminHasHealthCentre("hip-1");

        ResponseEntity<String> response = putDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, ""));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNull(demoAdminHipId());
    }

    @Test
    public void adminSaveWithIdNotInTheOrganisationsListIsRefused() {
        demoHasEkaHealthCentres("hip-1");
        demoAdminHasHealthCentre("hip-1");

        ResponseEntity<String> response = putDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-9"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("ABHA Health Facility ID 'hip-9' is not one of this organisation's health centres"));
        assertEquals("hip-1", demoAdminHipId());
    }

    // Only text is a health centre. A number would be stored, and every later save by that user would then fail on it.
    @Test
    public void adminSaveWithANumberAsAbhaHipIdIsRefused() {
        demoHasEkaHealthCentres("hip-1");
        demoAdminHasHealthCentre("hip-1");

        ResponseEntity<String> response = putDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, 12345));

        assertEquals(response.getBody(), HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody(), response.getBody().contains("ABHA Health Facility ID must be text"));
        assertEquals("hip-1", demoAdminHipId());
    }

    // A health centre dropped from the Eka list must not lock the user's record: the admin app sends the stored ID back
    // with every save, even when the field is hidden because ABHA is off.
    @Test
    public void unchangedHealthCentreNoLongerListedStillSaves() {
        demoHasEkaHealthCentres("hip-2");
        demoAdminHasHealthCentre("hip-1");

        ResponseEntity<String> response = putDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1").with(UserSettings.LOCALE, "hi"));

        assertEquals(response.getBody(), HttpStatus.CREATED, response.getStatusCode());
        assertEquals("hip-1", demoAdminHipId());
        assertEquals("hi", userRepository.findByUsername("demo-admin").getSettings().getString(UserSettings.LOCALE));
    }

    @Test
    public void organisationWithNoEkaSettingsRefusesAnyValueOnUpdateAndCreate() {
        // Users are not reset between tests here; start from a different stored ID so hip-1 is a change.
        demoAdminHasHealthCentre("hip-0");
        ResponseEntity<String> update = putDemoAdminWithSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1"));
        assertEquals(HttpStatus.BAD_REQUEST, update.getStatusCode());
        assertTrue(update.getBody(), update.getBody().contains("'hip-1' is not one of this organisation's health centres"));

        UserContract create = createValidUserContract("withhip@test", "With Hip");
        create.setSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1"));
        ResponseEntity<String> created = template.postForEntity(base + "/user", create, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, created.getStatusCode());
        assertNull(userRepository.findByUsername("withhip@test"));
    }

    @Test
    public void adminCreateWithListedIdSetsIt() {
        demoHasEkaHealthCentres("hip-1");
        UserContract create = createValidUserContract("withhip2@test", "With Hip Two");
        create.setSettings(new JsonObject().with(UserSettings.ABHA_HIP_ID, "hip-1"));

        ResponseEntity<String> created = template.postForEntity(base + "/user", create, String.class);

        assertEquals(created.getBody(), HttpStatus.CREATED, created.getStatusCode());
        assertEquals("hip-1", userRepository.findByUsername("withhip2@test").getSettings().getString(UserSettings.ABHA_HIP_ID));
    }

    private UserContract createValidUserContract(String username, String name) {
        UserContract userContract = new UserContract();
        userContract.setUsername(username);
        userContract.setName(name);
        userContract.setEmail(username + ".com" );
        userContract.setPhoneNumber("9876543210");
        userContract.setPassword("testPassword123");
        userContract.setOrganisationId(testOrganisation.getId());
        userContract.setOperatingIndividualScope(OperatingIndividualScope.None.name());
        return userContract;
    }

    private User createTestUser(String username, String name) {
        User demoAdmin = userRepository.findByUsername("demo-admin");
        User user = new UserBuilder()
                .withDefaultValuesForNewEntity()
                .userName(username)
                .organisationId(testOrganisation.getId())
                .withCatchment(testCatchment)
                .withOperatingIndividualScope(OperatingIndividualScope.None)
                .withAuditUser(demoAdmin)
                .build();
        user.setName(name);
        user.setEmail(username + ".com");

        return userRepository.save(user);
    }
}