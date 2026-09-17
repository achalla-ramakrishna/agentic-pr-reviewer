package com.codewalnut.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrReviewerApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReportsOk() {
        TestRestTemplate rest = new TestRestTemplate();
        ResponseEntity<String> response =
                rest.getForEntity("http://localhost:" + port + "/api/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("ok");
    }
}
