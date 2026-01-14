package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.*;
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