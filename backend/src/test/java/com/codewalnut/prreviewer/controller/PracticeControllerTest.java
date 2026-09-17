package com.codewalnut.prreviewer.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.prreviewer.dto.PracticeRequest;
import com.codewalnut.prreviewer.dto.PracticeResponse;
import com.codewalnut.prreviewer.domain.Category;
import com.codewalnut.prreviewer.domain.Language;
import com.codewalnut.prreviewer.domain.Severity;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PracticeControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void seededJavaPracticesArePresent() {
        ResponseEntity<PracticeResponse[]> response =
                rest.getForEntity(url("/api/practices"), PracticeResponse[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThanOrEqualTo(10);
        assertThat(response.getBody())
                .anyMatch(p -> p.title().equals("Empty catch block swallows exceptions"))
                .allMatch(p -> p.language() == Language.JAVA);
    }

    @Test
    void fullCrudLifecycle() {
        PracticeRequest createRequest =
                new PracticeRequest(
                        "Test-only practice",
                        "Created by an integration test.",
                        Category.STYLE,
                        Severity.LOW,
                        Language.JAVA,
                        "bad example",
                        "good example",
                        "N/A");

        ResponseEntity<PracticeResponse> created =
                rest.postForEntity(url("/api/practices"), createRequest, PracticeResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        UUID id = created.getBody().id();
        assertThat(created.getBody().active()).isTrue();

        ResponseEntity<PracticeResponse> fetched =
                rest.getForEntity(url("/api/practices/" + id), PracticeResponse.class);
        assertThat(fetched.getBody().title()).isEqualTo("Test-only practice");

        PracticeRequest updateRequest =
                new PracticeRequest(
                        "Updated practice title",
                        "Updated description.",
                        Category.STYLE,
                        Severity.MEDIUM,
                        Language.JAVA,
                        "bad",
                        "good",
                        "N/A");
        rest.put(url("/api/practices/" + id), updateRequest);

        ResponseEntity<PracticeResponse> afterUpdate =
                rest.getForEntity(url("/api/practices/" + id), PracticeResponse.class);
        assertThat(afterUpdate.getBody().title()).isEqualTo("Updated practice title");
        assertThat(afterUpdate.getBody().severity()).isEqualTo(Severity.MEDIUM);

        rest.exchange(
                url("/api/practices/" + id + "/active"),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("active", false)),
                PracticeResponse.class);

        ResponseEntity<PracticeResponse> afterDeactivate =
                rest.getForEntity(url("/api/practices/" + id), PracticeResponse.class);
        assertThat(afterDeactivate.getBody().active()).isFalse();

        rest.delete(url("/api/practices/" + id));

        ResponseEntity<Map> afterDelete = rest.getForEntity(url("/api/practices/" + id), Map.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidPractice() {
        PracticeRequest invalid =
                new PracticeRequest("", "", null, null, null, null, null, null);

        ResponseEntity<Map> response =
                rest.postForEntity(url("/api/practices"), invalid, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
