package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class MetadataDiffControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    public void shouldHitTheEndpoint() {
        ResponseEntity<String> response = template.postForEntity("/metadata/diff", null, String.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.OK)));
    }
}


