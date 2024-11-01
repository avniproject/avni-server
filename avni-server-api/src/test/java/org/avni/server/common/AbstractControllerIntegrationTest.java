package org.avni.server.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.TestWebContextService;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.net.URL;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
public abstract class AbstractControllerIntegrationTest {
    @LocalServerPort
    private int port;
    protected URL base;

    @Autowired
    public TestRestTemplate template;

    @Autowired
    private WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public OrganisationRepository organisationRepository;

    @Autowired
    private TestWebContextService testWebContextService;

    protected static ObjectMapper mapper = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    @Before
    public void setUp() throws Exception {
        em.getEntityManagerFactory().getCache().evictAll();
        this.base = new URL("http://localhost:" + port + "/");
        UserContextHolder.clear();
        template.getRestTemplate().setInterceptors(new ArrayList<>());
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext).build();
        testWebContextService.setRoles();
    }

    @After
    public void teardown() {
        UserContextHolder.clear();
        template.getRestTemplate().setInterceptors(new ArrayList<>());
        testWebContextService.setRoles();
        em.getEntityManagerFactory().getCache().evictAll();
    }

    protected void post(String path, Object json) {
        ResponseEntity<String> responseEntity = template.postForEntity(path, json, String.class);
        String body = String.valueOf(responseEntity.getBody());
        assertTrue(body, responseEntity.getStatusCode().is2xxSuccessful());
    }

    protected void post(String username, String path, Object json) {
        setUser(username);
        post(path, json);
    }

    protected String postForBody(String path, Object json) {
        ResponseEntity<String> responseEntity = template.postForEntity(path, json, String.class);
        return String.valueOf(responseEntity.getBody());
    }

    public void setUser(String name) {
        testWebContextService.setUser(name);
    }

    public void setUser(User user) {
        testWebContextService.setUser(user);
    }
}
